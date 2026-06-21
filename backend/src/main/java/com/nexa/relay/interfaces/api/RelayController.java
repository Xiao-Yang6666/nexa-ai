package com.nexa.relay.interfaces.api;

import com.nexa.relay.application.RelayAuthContext;
import com.nexa.relay.application.RelayForwardResult;
import com.nexa.relay.application.RelayForwardUseCase;
import com.nexa.relay.domain.vo.RelayDispatch;
import com.nexa.relay.domain.vo.RelayMode;
import com.nexa.relay.interfaces.api.dto.ErrorResponse;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
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
    public ResponseEntity<?> chatCompletions(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/chat/completions", body, actor);
    }

    /**
     * OpenAI legacy completions（F-3026，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/completions")
    public ResponseEntity<?> completions(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/completions", body, actor);
    }

    /**
     * Anthropic Claude 原生 messages（F-3035 RL-6，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/messages")
    public ResponseEntity<?> messages(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/messages", body, actor);
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
    public ResponseEntity<?> embeddings(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/embeddings", body, actor);
    }

    /**
     * Responses compact（F-3028 RL-2，compact 必须先于 responses 注册，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/responses/compact")
    public ResponseEntity<?> responsesCompact(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/responses/compact", body, actor);
    }

    /**
     * Responses（F-3029 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/responses")
    public ResponseEntity<?> responses(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/responses", body, actor);
    }

    /**
     * Edits legacy（F-3027 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/edits")
    public ResponseEntity<?> edits(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        return forwardRelay("/v1/edits", body, actor);
    }

    /**
     * Images variations（F-3030 RL-2 rd_ni，RelayNotImplemented → 501）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/images/variations")
    public ResponseEntity<?> imageVariations(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor) {
        RelayDispatch dispatch = useCase.resolveDispatch("/v1/images/variations");
        if (dispatch.mode() == RelayMode.NOT_IMPLEMENTED) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", ErrorResponse.of("not_implemented",
                            "/v1/images/variations is not implemented", "RELAY_NOT_IMPLEMENTED")));
        }
        return forwardRelay("/v1/images/variations", body, actor);
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
     * 统一 relay 转发入口：构造鉴权上下文 → 调主干编排 → 透传上游 status + headers + body。
     *
     * <p>从 {@link AuthenticatedActor} 取 userId/username 构造 {@link RelayAuthContext}；
     * group/tokenId/tokenName 依赖 TokenAuth 中间件（key 鉴权）落地——本期以缺省 group 占位。
     * 业务/集成异常由 {@code RelayExceptionHandler} 统一翻译为错误信封，本方法不写 try/catch。</p>
     *
     * @param path  对外端点路径（RL-2 分发用）
     * @param body  客户原始请求体字节
     * @param actor 当前认证操作者
     * @return 透传上游响应的 {@code ResponseEntity}
     */
    private ResponseEntity<?> forwardRelay(String path, byte[] body, AuthenticatedActor actor) {
        // TODO REQ-06: group/tokenId/tokenName 暂以缺省占位，待 TokenAuth（key 鉴权）接线后由真实 token 注入。
        RelayAuthContext authContext = new RelayAuthContext(
                actor.userId(), actor.username(), null, null, null);
        RelayForwardResult result = useCase.forward(path, body, authContext);
        return ResponseEntity.status(result.statusCode())
                .body(result.body());
    }
}
