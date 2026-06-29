package com.nexa.interfaces.relay.api;

import com.nexa.application.model.ListPublicModelsUseCase;
import com.nexa.application.relay.RelayAuthContext;
import com.nexa.application.relay.RelayForwardResult;
import com.nexa.application.relay.RelayForwardUseCase;
import com.nexa.application.relay.VideoProxyUseCase;
import com.nexa.domain.relay.vo.RelayDispatch;
import com.nexa.domain.relay.vo.RelayMode;
import com.nexa.infrastructure.relay.auth.RelayApiKeyAuthentication;
import com.nexa.interfaces.relay.api.dto.ErrorResponse;
import com.nexa.common.security.domain.rbac.AuthLevel;
import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import com.nexa.common.security.interfaces.annotation.CurrentActor;
import com.nexa.common.security.interfaces.annotation.RequireRole;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
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
    private final ListPublicModelsUseCase listPublicModelsUseCase;
    private final VideoProxyUseCase videoProxyUseCase;

    public RelayController(RelayForwardUseCase useCase,
                           ListPublicModelsUseCase listPublicModelsUseCase,
                           VideoProxyUseCase videoProxyUseCase) {
        this.useCase = useCase;
        this.listPublicModelsUseCase = listPublicModelsUseCase;
        this.videoProxyUseCase = videoProxyUseCase;
    }

    /**
     * OpenAI chat completions（F-3026 RL-1，TokenAuth）。
     *
     * @param body 原始请求 JSON
     * @return 转发响应（流式 SSE 或 JSON）
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                             HttpServletResponse response) {
        return forwardRelay("/v1/chat/completions", body, actor, response);
    }

    /**
     * OpenAI legacy completions（F-3026，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/completions")
    public ResponseEntity<?> completions(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                         HttpServletResponse response) {
        return forwardRelay("/v1/completions", body, actor, response);
    }

    /**
     * Anthropic Claude 原生 messages（F-3035 RL-6，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/messages")
    public ResponseEntity<?> messages(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                      HttpServletResponse response) {
        return forwardRelay("/v1/messages", body, actor, response);
    }

    /**
     * 模型列表（F-3034 / ML-6，对外名 A 全集，绝不含 B，TokenAuth）。
     *
     * <p>返回 {@code PublicModel.enabled=true AND deleted_at IS NULL} 的 public_name(A) 全集，
     * OpenAI {@code /v1/models} 列表信封格式。下架（enabled=false）或软删的模型不出现。</p>
     *
     * <p><b>零泄露</b>：数据仅经 {@link ListPublicModelsUseCase} 取公开名 A，全链不读
     * upstream_name(B)，从源头杜绝 B 泄露（COMPAT §2 候选层 B 不可见闸）。</p>
     *
     * @return OpenAI models 列表信封 {@code {"object":"list","data":[{id,object,created,owned_by}, ...]}}
     */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/v1/models")
    public ResponseEntity<?> models() {
        long created = Instant.now().getEpochSecond();
        List<Map<String, Object>> data = listPublicModelsUseCase.listEnabledPublicNames().stream()
                .map(publicName -> Map.<String, Object>of(
                        "id", publicName,
                        "object", "model",
                        "created", created,
                        "owned_by", "nexa"))
                .toList();
        return ResponseEntity.ok(Map.of("object", "list", "data", data));
    }

    /**
     * Embeddings（F-3033 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/embeddings")
    public ResponseEntity<?> embeddings(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                        HttpServletResponse response) {
        return forwardRelay("/v1/embeddings", body, actor, response);
    }

    /**
     * Responses compact（F-3028 RL-2，compact 必须先于 responses 注册，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/responses/compact")
    public ResponseEntity<?> responsesCompact(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                              HttpServletResponse response) {
        return forwardRelay("/v1/responses/compact", body, actor, response);
    }

    /**
     * Responses（F-3029 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/responses")
    public ResponseEntity<?> responses(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                       HttpServletResponse response) {
        return forwardRelay("/v1/responses", body, actor, response);
    }

    /**
     * Edits legacy（F-3027 RL-2，TokenAuth）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/edits")
    public ResponseEntity<?> edits(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                   HttpServletResponse response) {
        return forwardRelay("/v1/edits", body, actor, response);
    }

    /**
     * Images variations（F-3030 RL-2 rd_ni，RelayNotImplemented → 501）。
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/v1/images/variations")
    public ResponseEntity<?> imageVariations(@RequestBody byte[] body, @CurrentActor AuthenticatedActor actor,
                                             HttpServletResponse response) {
        RelayDispatch dispatch = useCase.resolveDispatch("/v1/images/variations");
        if (dispatch.mode() == RelayMode.NOT_IMPLEMENTED) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", ErrorResponse.of("not_implemented",
                            "/v1/images/variations is not implemented", "RELAY_NOT_IMPLEMENTED")));
        }
        return forwardRelay("/v1/images/variations", body, actor, response);
    }

    /**
     * 视频内容代理（F-4046 RL-5，UserAuth）。
     *
     * <p>归属校验（self-scope）→ 终态（SUCCESS）→ URL 解析 → SSRF → 流式回写
     * （{@code Cache-Control: max-age=86400}）。{@code data:} base64 直出；其余经 SSRF 校验后流式拉取。
     * 校验失败由 {@link RelayExceptionHandler} 翻译为 403/404。</p>
     *
     * @param taskId 视频任务 ID
     */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/v1/videos/{task_id}/content")
    public ResponseEntity<?> videoContent(@PathVariable("task_id") String taskId,
                                          @CurrentActor AuthenticatedActor actor,
                                          HttpServletResponse response) {
        VideoProxyUseCase.VideoContent content =
                videoProxyUseCase.resolveContent(taskId, (int) actor.userId());
        if (content.isInline()) {
            // data: base64 直出（已解码字节）。
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, VideoProxyUseCase.CACHE_CONTROL)
                    .header(HttpHeaders.CONTENT_TYPE,
                            content.mediaTypeOpt().orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .body(content.inlineBytes());
        }
        // 远端 URL：流式拉取并回写（io.Copy 等价）。直写 HttpServletResponse 走 servlet 输出流，
        // 绕过 ResponseEntity 的 HttpMessageConverter 链——后者无法把流式写出器序列化（见 forwardRelay 注释）。
        response.setStatus(HttpStatus.OK.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, VideoProxyUseCase.CACHE_CONTROL);
        writeStream(response, out -> videoProxyUseCase.streamRemote(content, out));
        return null;
    }

    /**
     * 统一 relay 转发入口：构造鉴权上下文 → 调主干编排 → 透传上游 status + headers + body。
     *
     * <p>身份来自 {@link RelayApiKeyAuthentication}（{@code RelayApiKeyAuthenticationFilter} 反查 tokens 表注入）：
     * {@code userId/username} 取自 {@link AuthenticatedActor}，{@code group/tokenId/tokenName} 取自 token 上下文，
     * 据此构造完整 {@link RelayAuthContext}——激活 REQ-03 选渠（真实 group）、REQ-05 计费（真实 group）、
     * REQ-06 key 减法校验（tokenId 非空时 KeyLimitGuard 生效）。理论上 /v1 必经 API-Key 鉴权，
     * 故 relay 上下文必存在；防御性缺失时回退缺省（group=default、tokenId=null）。
     * 业务/集成异常由 {@code RelayExceptionHandler} 统一翻译为错误信封，本方法不写 try/catch。</p>
     *
     * @param path  对外端点路径（RL-2 分发用）
     * @param body  客户原始请求体字节
     * @param actor 当前认证操作者
     * @return 透传上游响应的 {@code ResponseEntity}
     */
    private ResponseEntity<?> forwardRelay(String path, byte[] body, AuthenticatedActor actor,
                                           HttpServletResponse response) {
        RelayApiKeyAuthentication relayAuth = RelayApiKeyAuthentication.current().orElse(null);
        Long tokenId = relayAuth == null ? null : relayAuth.tokenId();
        String group = relayAuth == null ? null : relayAuth.group();
        String tokenName = relayAuth == null ? null : relayAuth.tokenName();
        RelayAuthContext authContext = new RelayAuthContext(
                actor.userId(), actor.username(), group, tokenId, tokenName);

        // RL-8 流式：客户 stream:true → 走 SSE 流式回写（逐 chunk flush）。
        //
        // 直写注入的 HttpServletResponse（servlet 输出流），返回 null 表示响应已由控制器自行处理，
        // 不再经 Spring 的返回值处理器。早期写法用 ResponseEntity.body(StreamingResponseBody) 会触发
        // 500：方法签名是 ResponseEntity<?>，泛型擦除成通配，Spring 用 ResponseEntityReturnValueHandler
        // 取出 body 后走 HttpMessageConverter 链去序列化它，而没有 converter 能把一个 Lambda 写成
        // text/event-stream，抛 HttpMessageNotWritableException。直写 response 彻底绕过该链路。
        //
        // 注意写出顺序：forwardStream 内的选渠/鉴权/key校验/模型组闸门若抛异常，发生在写出任何字节之前，
        // 此时响应未提交，可由 RelayExceptionHandler 正常翻译为错误信封；写出第一个 chunk 后的上游中断
        // 由 forwardStream 内部消化（不外抛），故此处无需 try/catch。
        if (useCase.wantsStream(body)) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            writeStream(response, out -> useCase.forwardStream(path, body, authContext, out));
            return null;
        }

        RelayForwardResult result = useCase.forward(path, body, authContext);
        return ResponseEntity.status(result.statusCode())
                .body(result.body());
    }

    /** 取 servlet 输出流执行流式写出器（直写绕过返回值处理器）。IO 异常向上抛由容器处理。 */
    private void writeStream(HttpServletResponse response, StreamWriter writer) {
        try {
            OutputStream out = response.getOutputStream();
            writer.writeTo(out);
            out.flush();
        } catch (IOException e) {
            // 客户端断连或写出失败：响应可能已部分提交，无法改写状态码。流式计费在 forwardStream
            // 内已按已交付 token 落 Log（防漏钱），此处仅终止写出。
            throw new RelayStreamWriteException(e);
        }
    }

    @FunctionalInterface
    private interface StreamWriter {
        void writeTo(OutputStream out) throws IOException;
    }

    /** 流式写出阶段的 IO 异常（响应可能已提交，仅用于终止写出，不再翻译为错误信封）。 */
    private static final class RelayStreamWriteException extends RuntimeException {
        RelayStreamWriteException(Throwable cause) {
            super(cause);
        }
    }
}
