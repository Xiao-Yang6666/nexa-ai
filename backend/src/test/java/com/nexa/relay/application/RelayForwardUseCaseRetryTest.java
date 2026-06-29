package com.nexa.relay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.relay.domain.exception.NoAvailableChannelException;
import com.nexa.relay.domain.model.RelayLog;
import com.nexa.relay.domain.port.AccountSelectionPort;
import com.nexa.relay.domain.port.SelectedAccount;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.port.UpstreamResponse;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.vo.LogType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RelayForwardUseCase RL-3 重试/错误处置单元测试（REQ-09，账号路由版）。
 *
 * <p>聚焦转发主干的重试循环行为：可重试码切账号重试、不可重试码直接返、重试耗尽返最后错误、
 * 错误 Log Type=5、计费仅在最终成功账号结算。选账号通过 {@link AccountSelectionPort} 桩驱动
 * （按 excludeAccountIds 返回不同账号或耗尽）。</p>
 */
class RelayForwardUseCaseRetryTest {

    private static final String PATH = "/v1/chat/completions";
    private static final byte[] BODY = "{\"model\":\"gpt-4o\"}".getBytes();

    private UpstreamHttpPort upstreamHttpPort;
    private RelayLogRepository logRepo;
    private UserQuotaAccount userQuotaAccount;
    private RelayForwardUseCase useCase;

    /** 可编程账号选择桩：按已尝试 accountId 集合返回下一个候选或空。 */
    private StubAccountSelection accountSelection;

    private RelayAuthContext auth;

    @BeforeEach
    void setUp() {
        UserModelAliasRepository l1Repo = mock(UserModelAliasRepository.class);
        logRepo = mock(RelayLogRepository.class);
        upstreamHttpPort = mock(UpstreamHttpPort.class);
        KeyLimitGuard keyLimitGuard = mock(KeyLimitGuard.class);
        PublicModelRepository publicModelRepo = mock(PublicModelRepository.class);
        userQuotaAccount = mock(UserQuotaAccount.class);
        accountSelection = new StubAccountSelection();

        when(l1Repo.findTargetByAlias(any(), anyString())).thenReturn(Optional.empty());
        when(publicModelRepo.findByPublicName(anyString())).thenReturn(Optional.empty());

        useCase = new RelayForwardUseCase(l1Repo, logRepo, upstreamHttpPort,
                new ObjectMapper(), keyLimitGuard, publicModelRepo, userQuotaAccount,
                groupCode -> Optional.empty(),
                (groupCode, userId, tokenId, model) -> true,
                accountSelection,
                userId -> BigDecimal.ONE);
        auth = new RelayAuthContext(7L, "alice", "default", null, null);
    }

    private static SelectedAccount account(long id) {
        return new SelectedAccount(id, "{\"key\":\"sk-key" + id + "\"}", "https://up" + id,
                "openai", BigDecimal.ONE, null, "gpt-4o", null, 0);
    }

    @Test
    void retryableErrorSwitchesAccountThenSucceeds() {
        // 首次选 account 1（503 可重试）→ 排除后选 account 2（200 成功）。
        accountSelection.add(account(1L));
        accountSelection.add(account(2L));

        UpstreamResponse err503 = UpstreamResponse.of(503, Map.of(), "{\"error\":{\"message\":\"busy\"}}".getBytes());
        UpstreamResponse ok = UpstreamResponse.of(200, Map.of(),
                "{\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5}}".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err503, ok);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        assertEquals(200, result.statusCode());
        verify(upstreamHttpPort, times(2)).send(any());
        verify(userQuotaAccount, times(1)).debit(anyLong(), any());
        // 一条 Type=5 错误 Log（account1 失败）+ 一条 Type=2 消费 Log（account2 成功）。
        ArgumentCaptor<RelayLog> logs = ArgumentCaptor.forClass(RelayLog.class);
        verify(logRepo, times(2)).save(logs.capture());
        assertTrue(logs.getAllValues().stream().anyMatch(l -> l.type() == LogType.ERROR));
        assertTrue(logs.getAllValues().stream().anyMatch(l -> l.type() == LogType.CONSUME));
    }

    @Test
    void nonRetryableErrorReturnsImmediatelyWithoutRetry() {
        accountSelection.add(account(1L));
        UpstreamResponse err400 = UpstreamResponse.of(400, Map.of(),
                "{\"error\":{\"message\":\"bad request\"}}".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err400);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        assertEquals(400, result.statusCode());
        // 不可重试：只调一次上游，不再换账号。
        verify(upstreamHttpPort, times(1)).send(any());
        verify(userQuotaAccount, never()).debit(anyLong(), any());
        assertTrue(new String(result.body()).contains("upstream request rejected (400)"));
    }

    @Test
    void rateLimitWriteBackOn429() {
        accountSelection.add(account(1L));
        UpstreamResponse err429 = UpstreamResponse.of(429, Map.of(),
                "{\"error\":{\"message\":\"rate limited\"}}".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err429);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        assertEquals(429, result.statusCode());
        // 429 → 账号限流回写。
        assertTrue(accountSelection.rateLimitedIds.contains(1L), "429 应回写账号限流");
    }

    @Test
    void retryExhaustionReturnsLastErrorWhenNoMoreAccounts() {
        // 只有一个账号，首次失败后排除即耗尽。
        accountSelection.add(account(1L));
        UpstreamResponse err500 = UpstreamResponse.of(500, Map.of(), "boom".getBytes());
        when(upstreamHttpPort.send(any(UpstreamRequest.class))).thenReturn(err500);

        RelayForwardResult result = useCase.forward(PATH, BODY, auth);

        // 重试耗尽态：返回最后一次上游错误（500），不抛异常。
        assertEquals(500, result.statusCode());
        verify(upstreamHttpPort, times(1)).send(any());
        verify(userQuotaAccount, never()).debit(anyLong(), any());
    }

    @Test
    void firstSelectionNoAccountStillThrows() {
        // 账号池为空 → 首次即抛 NoAvailableChannelException。
        org.junit.jupiter.api.Assertions.assertThrows(NoAvailableChannelException.class,
                () -> useCase.forward(PATH, BODY, auth));
    }

    /** 内存账号选择桩：按候选列表 + excludeAccountIds 返回下一个未尝试账号。 */
    static class StubAccountSelection implements AccountSelectionPort {
        private final java.util.List<SelectedAccount> pool = new java.util.ArrayList<>();
        final java.util.Set<Long> rateLimitedIds = new java.util.HashSet<>();
        final java.util.Set<Long> overloadedIds = new java.util.HashSet<>();

        void add(SelectedAccount a) {
            pool.add(a);
        }

        @Override
        public Optional<SelectedAccount> selectAccount(String group, String platform, java.util.Set<Long> excludeAccountIds) {
            java.util.Set<Long> excluded = excludeAccountIds == null ? java.util.Set.of() : excludeAccountIds;
            return pool.stream().filter(a -> !excluded.contains(a.accountId())).findFirst();
        }

        @Override
        public void markRateLimited(long accountId, Long resetAt) {
            rateLimitedIds.add(accountId);
        }

        @Override
        public void markOverloaded(long accountId, Long until) {
            overloadedIds.add(accountId);
        }
    }
}
