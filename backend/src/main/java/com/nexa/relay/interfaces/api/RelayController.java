package com.nexa.relay.interfaces.api;

import com.nexa.relay.application.RelayForwardUseCase;
import com.nexa.relay.domain.vo.RelayDispatch;
import com.nexa.relay.domain.vo.RelayMode;
import com.nexa.relay.interfaces.api.dto.ErrorResponse;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Relay 中继转发控制器（接口层，F-3026~F-3037 + F-4046 + F-6010~F-6011 对外端点）。
 *
 * <p>承载对外标准 OpenAI/Anthropic 协议端点（对齐 openapi relay 模块路径）：
 * <ul>
 *   <li>{@code POST /v1/chat/completions}         OpenAI chat（F-3026）</li>
 *   <li>{@code POST /v1/completions}               OpenAI legacy completions（F-3026）</li>
 *   <li>{@code POST /v1/messages}                  Anthropic Claude 原生（F-3035）</li>
 *   <li>{@code GET  /v1/models}                    模型列表（F-3034）</li>
 *   <li>{@code POST /v1/embeddings}                Embeddings（F-3033）</li>
 *   <li>{@code POST /v1/responses}                 Responses（F-3029）</li>
 *   <li>{@code POST /v1/responses/compact}         Responses Compact（F-3028）</li>
 *   <li>{@code POST /v1/edits}                     Edits legacy（F-3027）</li>
 *   <li>{@code POST /v1/images/variations}         NotImplemented（F-3030）</li>
 *   <li>{@code GET  /v1/videos/{task_id}/content}  视频代理（F-4046）</li>
 * </ul>
 * 完整转发逻辑（HTTP client + 流式 SSE）待后续 wave 注入；本期骨架确认路径分发 + RL-2 mode 解析正确。</p>
 */
@RestController
public class RelayController {

    private final RelayForwardUseCase useCase;

    public RelayController(RelayForwardUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * OpenAI chat completions（F-3026 RL-1，TokenAuth）。
     *
     * @param body 原始请求 JSON
     * @return 转发响应（流式 SSE 或 JSON）
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestBody byte[] body) {
        return forwardRelay("/v1/chat/completions", body);
    }

    /**
     * OpenAI legacy completions（F-3026，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/completions")
    public ResponseEntity<?> completions(@RequestBody byte[] body) {
        return forwardRelay("/v1/completions", body);
    }

    /**
     * Anthropic Claude 原生 messages（F-3035 RL-6，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/messages")
    public ResponseEntity<?> messages(@RequestBody byte[] body) {
        return forwardRelay("/v1/messages", body);
    }

    /**
     * 模型列表（F-3034，对外名 A，不含 B，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/v1/models")
    public ResponseEntity<?> models() {
        // TODO(W3+): 返回 PlatformModelMapping.publicName ∪ PrefillGroup(type=model) 的 A 全集
        return ResponseEntity.ok(Map.of("object", "list", "data", java.util.List.of()));
    }

    /**
     * Embeddings（F-3033 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/embeddings")
    public ResponseEntity<?> embeddings(@RequestBody byte[] body) {
        return forwardRelay("/v1/embeddings", body);
    }

    /**
     * Responses compact（F-3028 RL-2，compact 必须先于 responses 注册，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/responses/compact")
    public ResponseEntity<?> responsesCompact(@RequestBody byte[] body) {
        return forwardRelay("/v1/responses/compact", body);
    }

    /**
     * Responses（F-3029 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/responses")
    public ResponseEntity<?> responses(@RequestBody byte[] body) {
        return forwardRelay("/v1/responses", body);
    }

    /**
     * Edits legacy（F-3027 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/edits")
    public ResponseEntity<?> edits(@RequestBody byte[] body) {
        return forwardRelay("/v1/edits", body);
    }

    /**
     * Images variations（F-3030 RL-2 rd_ni，RelayNotImplemented → 501）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/images/variations")
    public ResponseEntity<?> imageVariations(@RequestBody byte[] body) {
        RelayDispatch dispatch = useCase.resolveDispatch("/v1/images/variations");
        if (dispatch.mode() == RelayMode.NOT_IMPLEMENTED) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", ErrorResponse.of("not_implemented",
                            "/v1/images/variations is not implemented", "RELAY_NOT_IMPLEMENTED")));
        }
        return forwardRelay("/v1/images/variations", body);
    }

    /**
     * 视频内容代理（F-4046 RL-5，UserAuth）。
     *
     * @param taskId 视频任务 ID
     */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/v1/videos/{task_id}/content")
    public ResponseEntity<?> videoContent(@PathVariable("task_id") String taskId) {
        // TODO(W3+): 注入 VideoProxyUseCase，完成归属校验→终态校验→SSRF→io.Copy 流式回写（RL-5）
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", ErrorResponse.of("not_implemented",
                        "video proxy not yet wired (W3+ TODO)", null)));
    }

    /**
     * 统一 relay 转发入口（占位，待注入 HTTP client + 流式 SSE，TODO W3+）。
     */
    private ResponseEntity<?> forwardRelay(String path, byte[] body) {
        RelayDispatch dispatch = useCase.resolveDispatch(path);
        // TODO(W3+): 执行完整 RL-1/RL-7 链路（两层映射→鉴权→预扣→选渠→协议转换→调上游→计费→落 Log）
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", ErrorResponse.of("not_implemented",
                        "relay forwarding not yet wired (W3+ TODO)", null),
                        "relay_mode", dispatch.mode().name(),
                        "protocol", dispatch.format().wireValue()));
    }
}
