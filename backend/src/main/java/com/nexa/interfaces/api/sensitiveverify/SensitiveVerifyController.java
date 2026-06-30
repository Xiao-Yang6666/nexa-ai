package com.nexa.interfaces.api.sensitiveverify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.common.web.ApiResponse;
import com.nexa.application.sensitiveverify.VerifySensitiveActionCommand;
import com.nexa.application.sensitiveverify.VerifySensitiveActionUseCase;
import com.nexa.domain.sensitiveverify.exception.InvalidVerificationRequestException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通用敏感动作二次验证控制器（接口层，F-1038，sessionAuth）。
 *
 * <p>对齐 openapi {@code POST /api/verify}：接收不定结构请求体（{@code additionalProperties: true}，
 * 承载密码 / TOTP / passkey 断言三类可选凭据），任一因子校验通过即放行（200 SuccessResponse），
 * 全不通过则拒绝（403 ForbiddenError）。</p>
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP ⇄ 用例命令）——从松散 JSON 抽取三类凭据装配
 * {@link VerifySensitiveActionCommand}，调用 {@link VerifySensitiveActionUseCase}，不含任何验证/裁决逻辑
 * （裁决在领域服务、校验在端口，backend-engineer §2.1）。领域异常由 {@link SensitiveVerifyExceptionHandler}
 * 翻译为 403/400。</p>
 *
 * <p>请求体字段约定（容忍前端命名差异）：
 * <ul>
 *   <li>密码：{@code password}</li>
 *   <li>TOTP：{@code totp_code} 或 {@code totp} 或 {@code code}</li>
 *   <li>passkey 断言：{@code passkey}（对象，整体再序列化为 JSON 交验签）或 {@code passkey_assertion}（字符串）</li>
 * </ul>
 * 出参不回显任何提交的凭据（零敏感泄露，backend-engineer §3.4）。</p>
 *
 * <p><b>身份来源（安全声明，临时桩）</b>：sessionAuth 端点暂从请求头 {@code X-User-Id} 读取会话用户 id
 * （沿用账号域/passkey 域同款临时桩）。<b>这不是最终鉴权</b>——会话/JWT 过滤器接入后由认证主体提供身份
 * 并移除本头部；SecurityConfig 当前对非公开端点要求 {@code authenticated()}，在过滤器接入前本端点不无鉴权裸奔。</p>
 */
@RestController
public class SensitiveVerifyController {

    /** 会话用户 id 请求头（临时桩，待会话鉴权过滤器接入后由认证主体替代）。 */
    private static final String USER_ID_HEADER = "X-User-Id";

    private final VerifySensitiveActionUseCase verifySensitiveActionUseCase;
    private final ObjectMapper objectMapper;

    /**
     * @param verifySensitiveActionUseCase 二次验证用例（F-1038）
     * @param objectMapper                 JSON 序列化器（用于把 passkey 断言子对象再序列化为 JSON）
     */
    public SensitiveVerifyController(VerifySensitiveActionUseCase verifySensitiveActionUseCase,
                                     ObjectMapper objectMapper) {
        this.verifySensitiveActionUseCase = verifySensitiveActionUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * 通用敏感动作二次验证（F-1038，对齐 openapi {@code POST /api/verify}）。
     *
     * @param userId 会话用户 id（临时取自 {@code X-User-Id} 头）
     * @param body   不定结构凭据体（password / totp / passkey 任一即可；可为 null）
     * @return 验证通过返回成功回执（200）；未提供任何因子 → 400；因子均未通过 → 403（异常处理器翻译）
     */
    @PostMapping("/api/verify")
    public ApiResponse<Void> verify(@RequestHeader(name = USER_ID_HEADER) long userId,
                                    @RequestBody(required = false) Map<String, Object> body) {
        VerifySensitiveActionCommand command = toCommand(userId, body);
        verifySensitiveActionUseCase.verify(command);
        return ApiResponse.ok("verified");
    }

    /**
     * 从松散请求体抽取三类凭据装配命令（纯协议翻译，无业务逻辑）。
     *
     * @param userId 会话用户 id
     * @param body   请求体（可为 null）
     * @return 验证命令
     * @throws InvalidVerificationRequestException passkey 断言子对象序列化失败（请求体结构非法）
     */
    private VerifySensitiveActionCommand toCommand(long userId, Map<String, Object> body) {
        if (body == null) {
            // 空体：不携带任何因子，交领域服务判\"请求非法\"（400）。
            return new VerifySensitiveActionCommand(userId, null, null, null);
        }
        String password = asString(body.get("password"));
        String totp = firstNonBlank(asString(body.get("totp_code")),
                asString(body.get("totp")),
                asString(body.get("code")));
        String passkeyJson = extractPasskeyAssertion(body);
        return new VerifySensitiveActionCommand(userId, password, totp, passkeyJson);
    }

    /**
     * 抽取 passkey 断言 JSON：优先 {@code passkey_assertion}（已是字符串），否则把 {@code passkey} 子对象再序列化。
     *
     * @param body 请求体
     * @return 断言 JSON 串，或 null（未提交 passkey 因子）
     * @throws InvalidVerificationRequestException 子对象序列化失败
     */
    private String extractPasskeyAssertion(Map<String, Object> body) {
        String assertionStr = asString(body.get("passkey_assertion"));
        if (assertionStr != null && !assertionStr.isBlank()) {
            return assertionStr;
        }
        Object passkey = body.get("passkey");
        if (passkey == null) {
            return null;
        }
        if (passkey instanceof String s) {
            return s.isBlank() ? null : s;
        }
        try {
            // passkey 为对象：序列化回 JSON 交 passkey 域验签端口（端口入参约定为原始 assertion JSON）。
            return objectMapper.writeValueAsString(passkey);
        } catch (JsonProcessingException e) {
            // 不吞错：序列化失败属请求体结构非法，wrap 上下文抛领域异常（接口层翻 400）。
            throw new InvalidVerificationRequestException("invalid passkey assertion payload");
        }
    }

    /**
     * 安全地把任意值转字符串（null 透传 null，避免 NPE）。
     *
     * @param v 任意值
     * @return 字符串形式，或 null
     */
    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param candidates 候选串
     * @return 首个非空白者，全空返回 null
     */
    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }
}
