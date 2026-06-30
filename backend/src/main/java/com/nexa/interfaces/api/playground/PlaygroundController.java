package com.nexa.interfaces.api.playground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.application.playground.RunPlaygroundChatUseCase;
import com.nexa.domain.playground.exception.InvalidPlaygroundRequestException;
import com.nexa.domain.playground.vo.CredentialKind;
import com.nexa.domain.playground.vo.PlaygroundResult;
import com.nexa.shared.security.rbac.AuthLevel;
import com.nexa.shared.security.rbac.AuthenticatedActor;
import com.nexa.shared.security.annotation.CurrentActor;
import com.nexa.shared.security.annotation.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playground 站内对话试用控制器（接口层，F-4038）。
 *
 * <p>承载唯一对外端点 {@code POST /pg/chat/completions}（对齐 openapi playground 模块）：UserAuth +
 * Distribute + SystemPerformanceCheck。以临时令牌 {@code playground-<group>} 经 relay 转发，按用户实际
 * 额度计费（落 Log，C→A 客户视图）。关键安全闸：禁用 access token（{@code Authorization: Bearer} →403）。</p>
 *
 * <p>接口层只做「翻译」（backend-engineer §2.1）：解析凭据类型（Bearer→ACCESS_TOKEN，session→SESSION）、
 * 从请求体解析 {@code model}/{@code stream}/{@code messages} 三个最小字段（其余字节透传 relay），编排交
 * {@link RunPlaygroundChatUseCase}。授权/校验/上下文构造全在 domain，本类不写业务规则。</p>
 */
@RestController
public class PlaygroundController {

    /** Bearer 前缀（与 JwtAuthenticationFilter 同口径，用于识别 access token 凭据）。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final RunPlaygroundChatUseCase useCase;
    private final ObjectMapper objectMapper;

    /**
     * @param useCase      站内试用用例
     * @param objectMapper Jackson 解析器（仅解析最小入参字段，不重构整个请求体）
     */
    public PlaygroundController(RunPlaygroundChatUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    /**
     * 站内对话试用（F-4038，UserAuth；禁 access token）。
     *
     * @param body     原始 OpenAI chat/completions 请求体（透传 relay）
     * @param request  HTTP 请求（用于识别凭据来源 access token vs session）
     * @param operator 当前认证操作者（@CurrentActor 注入，缺失→401）
     * @return 透传上游 chat/completions 响应（流式 SSE 或非流式 JSON）；access token→403
     */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/pg/chat/completions")
    public ResponseEntity<byte[]> chatCompletions(@RequestBody byte[] body,
                                                  HttpServletRequest request,
                                                  @CurrentActor AuthenticatedActor operator) {
        // 凭据类型识别：携带 Authorization Bearer 即 access token（F-4038 安全闸据此拒绝），否则 session。
        CredentialKind credentialKind = resolveCredentialKind(request);

        // 解析最小入参字段（model/stream/messages 非空）；解析失败 = 非法请求体。
        ParsedRequest parsed = parseRequest(body);

        PlaygroundResult result = useCase.execute(
                operator.userId(), operator.username(),
                parsed.model(), parsed.stream(), parsed.hasMessages(),
                credentialKind, body);

        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.body());
    }

    /**
     * 据请求凭据来源映射 {@link CredentialKind}。
     *
     * <p>规则：存在 {@code Authorization: Bearer <token>} → {@link CredentialKind#ACCESS_TOKEN}（Playground 禁用）；
     * 否则视为 session 会话凭据 → {@link CredentialKind#SESSION}。与 {@code JwtAuthenticationFilter} 凭据提取
     * 同口径，使「禁 access token」判定基于真实凭据来源而非可伪造的请求体标志。</p>
     *
     * @param request HTTP 请求
     * @return 凭据类型
     */
    private CredentialKind resolveCredentialKind(HttpServletRequest request) {
        String authz = request.getHeader("Authorization");
        if (authz != null && authz.startsWith(BEARER_PREFIX) && !authz.substring(BEARER_PREFIX.length()).isBlank()) {
            return CredentialKind.ACCESS_TOKEN;
        }
        return CredentialKind.SESSION;
    }

    /**
     * 从请求体解析最小入参字段（model/stream/messages）。
     *
     * @param body 原始 JSON 字节
     * @return 解析结果
     * @throws InvalidPlaygroundRequestException JSON 非法或结构非对象
     */
    private ParsedRequest parseRequest(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InvalidPlaygroundRequestException("request body is required");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new InvalidPlaygroundRequestException("request body must be a JSON object");
            }
            JsonNode modelNode = root.get("model");
            String model = (modelNode != null && modelNode.isTextual()) ? modelNode.asText() : null;
            boolean stream = root.path("stream").asBoolean(false);
            JsonNode messages = root.get("messages");
            boolean hasMessages = messages != null && messages.isArray() && !messages.isEmpty();
            return new ParsedRequest(model, stream, hasMessages);
        } catch (InvalidPlaygroundRequestException e) {
            throw e;
        } catch (Exception e) {
            // wrap 保留错误链（backend-engineer §3.2 不吞错），对客户仍给稳定提示不回显正文。
            throw new InvalidPlaygroundRequestException("invalid JSON request body");
        }
    }

    /**
     * 请求体最小字段解析结果（接口层内部 DTO）。
     *
     * @param model       客户输入模型名 C（可空，domain 校验非空）
     * @param stream      是否流式
     * @param hasMessages messages 数组是否非空
     */
    private record ParsedRequest(String model, boolean stream, boolean hasMessages) {
    }
}
