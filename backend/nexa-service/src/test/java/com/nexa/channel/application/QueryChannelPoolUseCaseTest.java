package com.nexa.channel.application;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QueryChannelPoolUseCase 复现测试：验证按对外名 A 匹配 channel.models 的渠道池查询口径。
 *
 * <p>背景：模型页"供应渠道池"对每个对外模型 A 调 GET /api/channel/pool?upstream_model=A，
 * 后端按 channel.models CSV 是否含 A 投影成员。本测复现"名字一致应能绑上"的场景。</p>
 */
class QueryChannelPoolUseCaseTest {

    private Channel channel(long id, String group, String models) {
        return Channel.rehydrate(id, 1, "sk-" + id, ChannelStatus.ENABLED.code(), "ch" + id,
                0, "https://up" + id, models, group, 0L, 1,
                BigDecimal.ZERO, 0L, null, null, null, "", null, null, null, 0L);
    }

    @Test
    void matchesChannelWhenModelsCsvContainsPublicName() {
        ChannelRepository repo = mock(ChannelRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                channel(1L, "default", "gpt-4o,gpt-4o-mini"),
                channel(2L, "default", "claude-3-5-sonnet"),
                channel(3L, "default", "gpt-4o, claude-3-5-sonnet "))); // 含空格
        QueryChannelPoolUseCase useCase = new QueryChannelPoolUseCase(repo);

        // 按 A=gpt-4o 查：渠道 1 与 3 应命中（CSV 含 gpt-4o，大小写/空格不敏感）。
        List<Channel> pool = useCase.queryPool(null, "gpt-4o");
        assertEquals(2, pool.size(), "gpt-4o 应绑定渠道 1 和 3");
        assertTrue(pool.stream().anyMatch(c -> c.id() == 1L));
        assertTrue(pool.stream().anyMatch(c -> c.id() == 3L));
    }

    @Test
    void emptyWhenNoChannelDeclaresModel() {
        ChannelRepository repo = mock(ChannelRepository.class);
        when(repo.findAll()).thenReturn(List.of(channel(1L, "default", "gpt-4o")));
        QueryChannelPoolUseCase useCase = new QueryChannelPoolUseCase(repo);

        // 没有渠道声明 opus-4 → 真实"未绑渠道"。
        assertTrue(useCase.queryPool(null, "opus-4").isEmpty());
    }
}
