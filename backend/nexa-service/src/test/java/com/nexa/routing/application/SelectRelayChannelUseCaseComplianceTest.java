package com.nexa.routing.application;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.compliance.domain.exception.CrossBorderRoutingDeniedException;
import com.nexa.compliance.domain.vo.DataResidency;
import com.nexa.relay.domain.exception.NoAvailableChannelException;
import com.nexa.routing.application.port.ChannelResidencyPort;
import com.nexa.routing.application.port.ChannelSelectionPort;
import com.nexa.routing.domain.vo.ChannelCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SelectRelayChannelUseCase} 合规分组选渠护栏单测（F-5018，R2-03）。
 *
 * <p>验证「compliance 接选渠主干」：合规分组（compliance/domestic-only）选渠结果必须是境内渠道，
 * 绝不命中境外/驻地未知渠道（fail-closed）；非合规分组不受限、保持原单次选渠行为。用手写桩驱动
 * {@link ChannelSelectionPort}（加权随机抽签）与 {@link ChannelResidencyPort}（驻地数据源），
 * 隔离 DB/框架，纯逻辑验证护栏。</p>
 */
@DisplayName("合规分组选渠主干护栏 (F-5018)")
class SelectRelayChannelUseCaseComplianceTest {

    private static final String MODEL = "gpt-4o";

    private final DataResidency cn = DataResidency.domestic("cn-east");
    private final DataResidency us = DataResidency.overseas("us-west");

    /** 顺序桩：按调用次数依次返回预置候选 id（模拟加权随机每次抽不同渠道 + 排除集推进）。 */
    private static final class SequencedSelectionPort implements ChannelSelectionPort {
        private final long[] ids;
        int calls = 0;

        SequencedSelectionPort(long... ids) {
            this.ids = ids;
        }

        @Override
        public ChannelCandidate selectChannel(String group, String model, int priorityRetry) {
            return selectChannel(group, model, priorityRetry, Set.of());
        }

        @Override
        public ChannelCandidate selectChannel(String group, String model, int priorityRetry,
                                              Set<Long> excludeChannelIds) {
            // 返回排除集之外、序列中的下一个候选；用尽则 null（候选耗尽）。
            for (; calls < ids.length; calls++) {
                long id = ids[calls];
                if (excludeChannelIds == null || !excludeChannelIds.contains(id)) {
                    calls++;
                    return new ChannelCandidate(id, group, 0L, 1);
                }
            }
            return null;
        }
    }

    private static Channel channelWithId(long id) {
        Channel c = Channel.create(1, "sk-test", "gpt-4o", "ch" + id, "default",
                0L, 1, 1, "https://up", null, null, null, null, ChannelInfo.single());
        c.assignId(id);
        return c;
    }

    private ChannelRepository repoOf(Channel... channels) {
        Map<Long, Channel> byId = new HashMap<>();
        for (Channel c : channels) {
            byId.put(c.id(), c);
        }
        return new StubChannelRepository(byId);
    }

    private ChannelResidencyPort residencyMap(Map<Long, DataResidency> map) {
        return channel -> Optional.ofNullable(map.get(channel.id()));
    }

    @Test
    @DisplayName("非合规分组：单次选渠不受驻地限制，命中境外亦放行")
    void nonComplianceGroupUnrestricted() {
        Channel overseas = channelWithId(1L);
        var useCase = new SelectRelayChannelUseCase(
                new SequencedSelectionPort(1L), repoOf(overseas),
                residencyMap(Map.of(1L, us)));

        Channel selected = useCase.selectChannel("default", MODEL, Set.of());
        assertSame(overseas, selected, "非合规分组放行境外渠道");
    }

    @Test
    @DisplayName("合规分组：境内候选直接命中")
    void complianceGroupDomesticHit() {
        Channel domestic = channelWithId(1L);
        var useCase = new SelectRelayChannelUseCase(
                new SequencedSelectionPort(1L), repoOf(domestic),
                residencyMap(Map.of(1L, cn)));

        Channel selected = useCase.selectChannel("compliance", MODEL, Set.of());
        assertSame(domestic, selected, "合规分组命中境内渠道");
    }

    @Test
    @DisplayName("合规分组：先抽到境外则排除重选，最终落到境内渠道")
    void complianceGroupSkipsOverseasThenHitsDomestic() {
        Channel overseas = channelWithId(1L);
        Channel domestic = channelWithId(2L);
        var port = new SequencedSelectionPort(1L, 2L); // 先抽境外1，排除后抽境内2
        var useCase = new SelectRelayChannelUseCase(
                port, repoOf(overseas, domestic),
                residencyMap(Map.of(1L, us, 2L, cn)));

        Channel selected = useCase.selectChannel("domestic-only", MODEL, Set.of());
        assertSame(domestic, selected, "排除境外后命中境内");
        assertEquals(2, port.calls, "抽签发生两次：境外被排除后重选");
    }

    @Test
    @DisplayName("合规分组：候选全境外 → 抛 CrossBorderRoutingDeniedException（合规拒绝，非 503）")
    void complianceGroupAllOverseasDenied() {
        Channel overseas1 = channelWithId(1L);
        Channel overseas2 = channelWithId(2L);
        var useCase = new SelectRelayChannelUseCase(
                new SequencedSelectionPort(1L, 2L), repoOf(overseas1, overseas2),
                residencyMap(Map.of(1L, us, 2L, us)));

        assertThrows(CrossBorderRoutingDeniedException.class,
                () -> useCase.selectChannel("compliance", MODEL, Set.of()));
    }

    @Test
    @DisplayName("合规分组：驻地未知 fail-closed，无境内候选 → 拒绝出境")
    void complianceGroupUnknownResidencyFailClosed() {
        Channel unknown = channelWithId(1L);
        var useCase = new SelectRelayChannelUseCase(
                new SequencedSelectionPort(1L), repoOf(unknown),
                residencyMap(Map.of())); // 驻地未知 → empty

        assertThrows(CrossBorderRoutingDeniedException.class,
                () -> useCase.selectChannel("compliance", MODEL, Set.of()),
                "驻地未知不可证明境内 → fail-closed 拒绝");
    }

    @Test
    @DisplayName("合规分组：本就无任何候选 → 维持 NoAvailableChannelException（503 语义不变）")
    void complianceGroupNoCandidateKeeps503() {
        var useCase = new SelectRelayChannelUseCase(
                new SequencedSelectionPort(), repoOf(),
                residencyMap(Map.of()));

        assertThrows(NoAvailableChannelException.class,
                () -> useCase.selectChannel("compliance", MODEL, Set.of()));
    }

    /** 极简渠道仓储桩（按 id 命中，不命中返回空，模拟选中渠道已不存在）。 */
    private record StubChannelRepository(Map<Long, Channel> byId) implements ChannelRepository {
        @Override
        public Optional<Channel> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Channel save(Channel channel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Channel> findPage(String group, Integer type, String tag,
                                                Integer status, com.nexa.channel.domain.vo.Pagination p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count(String group, Integer type, String tag, Integer status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Channel> search(String keyword, com.nexa.channel.domain.vo.Pagination p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countSearch(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Channel> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Channel> findByTag(String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Channel> findByIds(java.util.List<Long> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateStatusByIds(java.util.List<Long> ids,
                                     com.nexa.channel.domain.vo.ChannelStatus status) {
            throw new UnsupportedOperationException();
        }
    }
}
