package com.nexa.passkey.interfaces.api;

import com.nexa.shared.web.ApiResponse;
import com.nexa.account.interfaces.api.dto.UserView;
import com.nexa.passkey.application.LoginWithPasskeyUseCase;
import com.nexa.passkey.application.ManagePasskeyUseCase;
import com.nexa.passkey.application.RegisterPasskeyUseCase;
import com.nexa.passkey.application.VerifyPasskeyUseCase;
import com.nexa.passkey.application.port.PasskeyUserDirectory;
import com.nexa.passkey.interfaces.api.dto.PasskeyView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Passkey（WebAuthn）用户端控制器（接口层，F-1028 注册 / F-1029 登录 / F-1030 二次验证 / F-1031 查询+删除）。
 *
 * <p>承载本人与登录入口的 passkey 端点（对齐 openapi）：
 * <ul>
 *   <li>{@code POST /api/user/self/passkey/register/begin|finish}（F-1028，sessionAuth）</li>
 *   <li>{@code POST /api/user/passkey/login/begin|finish}（F-1029，security: [] 公开）</li>
 *   <li>{@code POST /api/user/self/passkey/verify/begin|finish}（F-1030，sessionAuth）</li>
 *   <li>{@code GET/DELETE /api/user/self/passkey}（F-1031，sessionAuth）</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP ⇄ 用例），不含业务逻辑——ceremony 编排在 application、
 * 验签在基础设施端口、领域不变量在聚合（backend-engineer §2.1）。WebAuthn options/响应为不定结构 JSON，
 * begin 出参直接透传端口产出的 options JSON 串（包进 {@code ApiResponse.data}）；finish 入参为
 * authenticator 原始响应（{@code String} body，端口解析）。领域异常由 {@link PasskeyExceptionHandler} 翻译。</p>
 *
 * <p><b>身份来源（安全声明，临时桩）</b>：sessionAuth 端点暂从请求头 {@code X-User-Id} 读取会话用户 id
 * （沿用 {@link com.nexa.account.interfaces.api.OAuthBindingController} 同款临时桩）。<b>这不是最终鉴权</b>——
 * SecurityConfig 对非公开端点要求 {@code authenticated()}，会话/JWT 过滤器接入后由认证主体提供身份并移除本头部。
 * 登录 begin/finish 为公开端点（无需会话）。在过滤器接入前 self 端点不会无鉴权裸奔。</p>
 */
@RestController
public class PasskeyController {

    /** 会话用户 id 请求头（临时桩，待会话鉴权过滤器接入后由认证主体替代）。 */
    private static final String USER_ID_HEADER = "X-User-Id";

    private final RegisterPasskeyUseCase registerPasskeyUseCase;
    private final LoginWithPasskeyUseCase loginWithPasskeyUseCase;
    private final VerifyPasskeyUseCase verifyPasskeyUseCase;
    private final ManagePasskeyUseCase managePasskeyUseCase;

    /**
     * @param registerPasskeyUseCase  注册用例（F-1028）
     * @param loginWithPasskeyUseCase 登录用例（F-1029）
     * @param verifyPasskeyUseCase    二次验证用例（F-1030）
     * @param managePasskeyUseCase    查询/删除用例（F-1031）
     */
    public PasskeyController(RegisterPasskeyUseCase registerPasskeyUseCase,
                            LoginWithPasskeyUseCase loginWithPasskeyUseCase,
                            VerifyPasskeyUseCase verifyPasskeyUseCase,
                            ManagePasskeyUseCase managePasskeyUseCase) {
        this.registerPasskeyUseCase = registerPasskeyUseCase;
        this.loginWithPasskeyUseCase = loginWithPasskeyUseCase;
        this.verifyPasskeyUseCase = verifyPasskeyUseCase;
        this.managePasskeyUseCase = managePasskeyUseCase;
    }

    // ============ F-1028 注册 ============

    /**
     * 注册 begin（F-1028，对齐 openapi {@code POST /api/user/self/passkey/register/begin}）。
     *
     * @param userId 会话用户 id（临时取自 {@code X-User-Id} 头）
     * @return 成功信封，data 为 CredentialCreationOptions（JSON 串透传）
     */
    @PostMapping("/api/user/self/passkey/register/begin")
    public ApiResponse<String> registerBegin(@RequestHeader(name = USER_ID_HEADER) long userId) {
        return ApiResponse.okData(registerPasskeyUseCase.begin(userId));
    }

    /**
     * 注册 finish（F-1028，对齐 openapi {@code POST /api/user/self/passkey/register/finish}）。
     *
     * @param userId               会话用户 id
     * @param attestationResponse  authenticator attestation 响应（原始 JSON body）
     * @return 成功回执
     */
    @PostMapping("/api/user/self/passkey/register/finish")
    public ApiResponse<Void> registerFinish(@RequestHeader(name = USER_ID_HEADER) long userId,
                                            @RequestBody String attestationResponse) {
        registerPasskeyUseCase.finish(userId, attestationResponse);
        return ApiResponse.ok("passkey registered");
    }

    // ============ F-1029 登录 ============

    /**
     * 登录 begin（F-1029，公开，对齐 openapi {@code POST /api/user/passkey/login/begin}）。
     *
     * @param body 可选 {@code {username}}（指定用户名定位本人凭据；缺省走可发现凭据）
     * @return 成功信封，data 为 CredentialRequestOptions（JSON 串透传）
     */
    @PostMapping("/api/user/passkey/login/begin")
    public ApiResponse<String> loginBegin(@RequestBody(required = false) Map<String, String> body) {
        String username = body == null ? null : body.get("username");
        return ApiResponse.okData(loginWithPasskeyUseCase.begin(username));
    }

    /**
     * 登录 finish（F-1029，公开，对齐 openapi {@code POST /api/user/passkey/login/finish}）。
     *
     * <p>验签通过后返回登录用户 {@link UserView}（客户视图零敏感；access_token 不进 body，产品铁律
     * 沿用账号域登录约定）。</p>
     *
     * @param assertionResponse authenticator assertion 响应（原始 JSON body）
     * @return 成功信封，data 为 UserView
     */
    @PostMapping("/api/user/passkey/login/finish")
    public ApiResponse<UserView> loginFinish(@RequestBody String assertionResponse) {
        PasskeyUserDirectory.UserSnapshot snapshot = loginWithPasskeyUseCase.finish(assertionResponse);
        return ApiResponse.okData(toUserView(snapshot));
    }

    // ============ F-1030 二次验证 ============

    /**
     * 二次验证 begin（F-1030，对齐 openapi {@code POST /api/user/self/passkey/verify/begin}）。
     *
     * @param userId 会话用户 id
     * @return 成功信封，data 为验证 options（JSON 串透传）
     */
    @PostMapping("/api/user/self/passkey/verify/begin")
    public ApiResponse<String> verifyBegin(@RequestHeader(name = USER_ID_HEADER) long userId) {
        return ApiResponse.okData(verifyPasskeyUseCase.begin(userId));
    }

    /**
     * 二次验证 finish（F-1030，对齐 openapi {@code POST /api/user/self/passkey/verify/finish}）。
     *
     * @param userId            会话用户 id
     * @param assertionResponse authenticator assertion 响应（原始 JSON body）
     * @return 成功回执
     */
    @PostMapping("/api/user/self/passkey/verify/finish")
    public ApiResponse<Void> verifyFinish(@RequestHeader(name = USER_ID_HEADER) long userId,
                                          @RequestBody String assertionResponse) {
        verifyPasskeyUseCase.finish(userId, assertionResponse);
        return ApiResponse.ok("passkey verified");
    }

    // ============ F-1031 查询 / 删除 ============

    /**
     * 查询本人 passkey 状态（F-1031，对齐 openapi {@code GET /api/user/self/passkey}）。
     *
     * <p>出参为 {@code {items: PasskeyView[]}}（0 或 1 条，单 passkey 语义），对齐 openapi data 结构。</p>
     *
     * @param userId 会话用户 id
     * @return 成功信封，data 为 {@code {items: [...]}}
     */
    @GetMapping("/api/user/self/passkey")
    public ApiResponse<Map<String, List<PasskeyView>>> listSelf(
            @RequestHeader(name = USER_ID_HEADER) long userId) {
        List<PasskeyView> items = managePasskeyUseCase.findByUser(userId)
                .map(PasskeyView::from)
                .map(List::of)
                .orElseGet(List::of);
        // openapi data 为 { items: [...] } 对象包裹（非裸数组）。
        return ApiResponse.okData(Map.of("items", items));
    }

    /**
     * 删除本人 passkey（F-1031，对齐 openapi {@code DELETE /api/user/self/passkey}）。
     *
     * <p>幂等：本人无 passkey 也回成功（openapi 200 = SuccessResponse）。{@code credential_id} 查询参数
     * 在单 passkey 语义下可忽略（每用户至多一条，按归属删除即可），保留参数以兼容契约。</p>
     *
     * @param userId       会话用户 id
     * @param credentialId 可选凭据标识（单 passkey 语义下忽略，仅契约兼容）
     * @return 成功回执
     */
    @DeleteMapping("/api/user/self/passkey")
    public ApiResponse<Void> deleteSelf(
            @RequestHeader(name = USER_ID_HEADER) long userId,
            @RequestParam(name = "credential_id", required = false) String credentialId) {
        managePasskeyUseCase.deleteSelf(userId);
        return ApiResponse.ok("passkey deleted");
    }

    /**
     * 中性用户快照 → 账号域客户视图 {@link UserView}（复用全站统一登录视图 schema）。
     *
     * <p>显式逐字段映射，零敏感（快照本身已是客户视图投影）。</p>
     *
     * @param s 中性用户快照
     * @return 客户视图
     */
    private static UserView toUserView(PasskeyUserDirectory.UserSnapshot s) {
        return new UserView(
                s.id(),
                s.username(),
                s.role(),
                s.status(),
                s.quota(),
                s.affCode(),
                s.email(),
                s.lastLoginAt());
    }
}
