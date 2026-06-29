package com.nexa.relay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.repository.AccountRepository;
import com.nexa.relay.domain.exception.VideoTaskException;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.task.application.QueryTaskUseCase;
import com.nexa.task.domain.model.Task;
import com.nexa.task.domain.vo.TaskPlatform;
import com.nexa.task.domain.vo.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * VideoProxyUseCase 单元测试（RL-5 F-4046，REQ-11）。
 *
 * <p>覆盖：self-scope 归属校验（404 不存在/非本人）、终态校验（SUCCESS）、data: base64 解码、
 * http(s) URL SSRF 防护、账号凭证提取。不测流式回写（流式行为由 upstreamHttpPort 保证）。
 * 凭证来源已从旧 channel 迁移到 account：task.channelId 现承载 accountId，从账号 credentials JSON 取 key。</p>
 */
class VideoProxyUseCaseTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final QueryTaskUseCase queryTaskUseCase = mock(QueryTaskUseCase.class);
    private final AccountRepository accountRepo = mock(AccountRepository.class);
    private final UpstreamHttpPort upstreamHttpPort = mock(UpstreamHttpPort.class);
    private final VideoProxyUseCase useCase = new VideoProxyUseCase(
            queryTaskUseCase, accountRepo, upstreamHttpPort, mapper);

    private Task task(TaskStatus status, String data, Integer channelId) {
        return Task.rehydrate(1L, "task_1", TaskPlatform.KLING, 7, "free", channelId, 100L,
                "VIDEO", status, null, 1000L, null, null, "0%", null, data, null, null, 2000L, 2000L);
    }

    /** 构造一个账号，credentials 为 {@code {"key":"..."}}（VideoProxy 从中取 key）。 */
    private Account account(long id, String key) {
        return Account.rehydrate(id, "acc" + id, "google", "api_key",
                "{\"key\":\"" + key + "\"}", "https://up" + id, 0, 0,
                "active", null, null, null, null, true,
                java.math.BigDecimal.ONE, null, 0, null, false,
                null, null, null, null, "model", List.of(), null, null);
    }

    @Test
    void notFoundWhenTaskDoesNotExist() {
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7)).thenReturn(Optional.empty());
        VideoTaskException ex = assertThrows(VideoTaskException.class,
                () -> useCase.resolveContent("tk_1", 7));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void notSuccessWhenStatusNotSuccess() {
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7))
                .thenReturn(Optional.of(task(TaskStatus.IN_PROGRESS, "{\"result_url\":\"http://x\"}", 1)));
        VideoTaskException ex = assertThrows(VideoTaskException.class,
                () -> useCase.resolveContent("tk_1", 7));
        assertTrue(ex.getMessage().contains("not finished"));
    }

    @Test
    void dataUrlDecodesToInline() {
        String data = "{\"result_url\":\"data:video/mp4;base64,SGVsbG8=\"}";
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7))
                .thenReturn(Optional.of(task(TaskStatus.SUCCESS, data, 1)));
        VideoProxyUseCase.VideoContent content = useCase.resolveContent("tk_1", 7);
        assertTrue(content.isInline());
        assertEquals("video/mp4", content.mediaType());
        assertEquals("Hello", new String(content.inlineBytes())); // base64("SGVsbG8=") = "Hello"
    }

    @Test
    void httpUrlPassesSsrfAndBecomesRemote() {
        String data = "{\"video_url\":\"https://example.com/vid.mp4\"}";
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7))
                .thenReturn(Optional.of(task(TaskStatus.SUCCESS, data, 2)));
        when(accountRepo.findById(2L)).thenReturn(Optional.of(account(2L, "sk-api")));
        VideoProxyUseCase.VideoContent content = useCase.resolveContent("tk_1", 7);
        assertTrue(content.isRemote());
        assertEquals("https://example.com/vid.mp4", content.url());
        assertEquals("sk-api", content.apiKey());
    }

    @Test
    void ssrfBlocksInternalUrl() {
        String data = "{\"result_url\":\"http://localhost:8080/vid\"}";
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7))
                .thenReturn(Optional.of(task(TaskStatus.SUCCESS, data, null)));
        VideoTaskException ex = assertThrows(VideoTaskException.class,
                () -> useCase.resolveContent("tk_1", 7));
        assertTrue(ex.getMessage().contains("ssrf"));
    }

    @Test
    void extractsUrlFromMultipleFieldNames() {
        // result_url 优先级高于 url。
        String data = "{\"url\":\"https://wrong.com\",\"result_url\":\"https://right.com\"}";
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7))
                .thenReturn(Optional.of(task(TaskStatus.SUCCESS, data, null)));
        VideoProxyUseCase.VideoContent content = useCase.resolveContent("tk_1", 7);
        assertTrue(content.isRemote());
        assertEquals("https://right.com", content.url());
    }

    @Test
    void nonJsonDataTreatedAsRawUrl() {
        String data = "https://example.com/direct.mp4";
        when(queryTaskUseCase.getByTaskIdForUser("tk_1", 7))
                .thenReturn(Optional.of(task(TaskStatus.SUCCESS, data, null)));
        VideoProxyUseCase.VideoContent content = useCase.resolveContent("tk_1", 7);
        assertTrue(content.isRemote());
        assertEquals("https://example.com/direct.mp4", content.url());
    }
}
