package com.nexa.interfaces.api.account;

import com.nexa.application.account.GetSelfUserUseCase;
import com.nexa.application.account.LoginCommand;
import com.nexa.application.account.LoginResult;
import com.nexa.application.account.LoginUseCase;
import com.nexa.application.account.RegisterUserCommand;
import com.nexa.application.account.RegisterUserUseCase;
import com.nexa.application.account.ResetPasswordCommand;
import com.nexa.application.account.ResetPasswordUseCase;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.account.dto.LoginRequest;
import com.nexa.interfaces.api.account.dto.RegisterRequest;
import com.nexa.interfaces.api.account.dto.ResetPasswordRequest;
import com.nexa.interfaces.api.account.dto.UserVO;
import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.rbac.AuthenticatedActor;
import com.nexa.common.security.annotation.CurrentActor;
import com.nexa.common.security.annotation.RequireRole;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号接口层控制器（注册 / 登录）。
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP ⇄ 用例命令/结果），不含任何业务逻辑
 * （backend-engineer §2.1）。具体流程：
 * <ol>
 *   <li>反序列化 + Bean Validation 校验请求 DTO（协议级）；</li>
 *   <li>翻译为应用层命令（{@link RegisterUserCommand}/{@link LoginCommand}）；</li>
 *   <li>调用用例；</li>
 *   <li>把结果投影为出参 DTO（客户视图零敏感字段）。</li>
 * </ol>
 * 领域/业务异常由 {@code GlobalExceptionHandler} 统一翻译为 HTTP 状态码 + 错误信封，
 * 本类不 try/catch 业务异常。</p>
 *
 * <p>对齐 openapi.yaml：{@code POST /api/user/register}（F-1001）、
 * {@code POST /api/user/login}（F-1002）、{@code GET /api/user/logout}（F-1003）、
 * {@code POST /api/user/reset}（F-1007）。</p>
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /**
     * 会话凭据 cookie 名（对齐 openapi securitySchemes 的 {@code name: session}，
     * 以及 {@code JwtAuthenticationFilter} 回退读取的 {@code SESSION_COOKIE}）。
     * 三端一致：契约 / 登录下发 / 过滤器读取 都用 "session"。
     */
    private static final String SESSION_COOKIE = "session";

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final GetSelfUserUseCase getSelfUserUseCase;

    /**
     * @param registerUserUseCase 注册用例（应用层）
     * @param loginUseCase        登录用例（应用层）
     * @param resetPasswordUseCase 重置密码用例（应用层，F-1007）
     * @param getSelfUserUseCase  本人信息查询用例（应用层，F-1045）
     */
    public UserController(RegisterUserUseCase registerUserUseCase,
                          LoginUseCase loginUseCase,
                          ResetPasswordUseCase resetPasswordUseCase,
                          GetSelfUserUseCase getSelfUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.getSelfUserUseCase = getSelfUserUseCase;
    }

    /**
     * 邮箱密码注册（F-1001）。
     *
     * <p>成功返回 {@code SuccessResponse}（仅 success/message，<b>不下发</b> password/access_token/
     * 用户详情，对齐 openapi register 200 schema）。</p>
     *
     * @param request 注册请求（已校验）
     * @return 成功信封
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        // 协议翻译：HTTP DTO → 应用层命令。不在此判任何业务规则。
        RegisterUserCommand command = new RegisterUserCommand(
                request.username(),
                request.password(),
                request.email(),
                request.verificationCode(),
                request.affCode());
        registerUserUseCase.register(command);
        // 注册仅回执成功，不回显账号信息（符合 openapi register 200 = SuccessResponse）。
        return ApiResponse.ok("register success");
    }

    /**
     * 邮箱密码登录（F-1002）。
     *
     * <p>成功返回 {@code ApiResponse} 且 {@code data=UserVO}（客户视图，零敏感字段，
     * body 结构不变，不破坏 openapi 契约）。<b>额外</b>通过 {@code Set-Cookie} 把已签发的
     * 会话凭据（JWT 紧凑串）下发到 {@code session} cookie——这是补齐 S10 联调缺口的关键：
     * 此前 {@code result.token()} 被整段丢弃，浏览器拿不到任何凭证，后续 self-scope 端点全 403。</p>
     *
     * <p>三端对齐：cookie 名 {@code session} 对齐 ① openapi {@code securitySchemes.sessionAuth}
     * （in: cookie / name: session）② {@code JwtAuthenticationFilter} 回退读取的 cookie 名。
     * 因此后续请求带上此 cookie，过滤器即可解析身份注入 SecurityContext，self-scope 鉴权放行。</p>
     *
     * <p>Cookie 属性（本地/局域网跨端口 http 联调取舍）：
     * <ul>
     *   <li>{@code HttpOnly}：禁 JS 读取，防 XSS 窃取会话令牌（安全默认）。</li>
     *   <li>{@code Path=/}：全站接口可带。</li>
     *   <li>{@code SameSite=Lax}：本地 http（无 https）环境下 {@code SameSite=None} 必须配 {@code Secure}，
     *       否则浏览器拒收；故用 Lax。同主机不同端口（如 localhost:3100 → localhost:8080）属同站，Lax 可带。</li>
     *   <li><b>不</b>设 {@code Secure}：本地为明文 http，设了反而不下发。生产 https 部署应改为 Secure + SameSite=None（如需跨站）。</li>
     * </ul>
     * UserVO 内仍<b>绝不含</b> access_token（产品铁律）——令牌只走 HttpOnly cookie，body 不回显。</p>
     *
     * @param request  登录请求（已校验）
     * @param response HTTP 响应（用于下发 Set-Cookie 会话凭据）
     * @return 成功信封，data 为用户客户视图
     */
    @PostMapping("/login")
    public ApiResponse<UserVO> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse response) {
        LoginCommand command = new LoginCommand(request.username(), request.password());
        LoginResult result = loginUseCase.login(command);
        // 下发会话凭据：把签发的 JWT 放入 HttpOnly 的 session cookie（对齐契约 + 过滤器读取方式）。
        // 用手写 Set-Cookie 头而非 jakarta Cookie，因后者不支持 SameSite 属性。
        response.addHeader(HttpHeaders.SET_COOKIE, buildSessionCookie(result.token()));
        // body 仅投影客户视图；result.token() 不放进 body（不下发 access_token，产品铁律）。
        return ApiResponse.okData(UserVO.from(result.user()));
    }

    /**
     * 获取本人账户信息（F-1045，对齐 openapi {@code GET /api/user/self}）。
     *
     * <p>S10 联调缺口补齐：该路径此前只挂了 DELETE（注销账号），未实现 GET —— 浏览器打 GET 命中
     * 405（Allow: DELETE），被 Security 翻成 403，导致前端 dashboard 的 {@code useKpi}
     * {@code Promise.all([getSelfAccount, getSpendStat])} 整体 reject、4 张 KPI 卡渲染不出。
     * 补完本 GET 后 self 返 200，前端零改动即可成功加载 KPI。</p>
     *
     * <p><b>self-scope 鉴权</b>：{@link RequireRole}({@link AuthLevel#USER}) 要求至少登录；查询目标
     * 恒取自会话内 {@link AuthenticatedActor#userId()}，<b>不接受</b>外部传入 id——从根上杜绝越权
     * 读他人账户（ROLE-PERMISSION-MATRIX §3 self-scope）。未认证由 {@code @CurrentActor} 抛
     * 鉴权异常（→401，shared SecurityExceptionHandler 翻译）。</p>
     *
     * <p><b>客户视图零敏感泄露（产品铁律）</b>：复用登录已验证干净的 {@link UserVO#from(...)} 投影，
     * 仅下发本人可见账户字段（id/username/role/status/quota/aff_code/email/last_login_at），
     * 投影时<b>根本不读取</b> passwordHash / 成本 / 利润 / 上游真实模型 / 供应商等字段，
     * 从源头杜绝下发。账号不存在（已注销）由 {@code GlobalExceptionHandler} 翻译为 404。</p>
     *
     * @param actor 当前认证操作者（查询目标恒为本人，self-scope）
     * @return 成功信封，data 为本人客户视图 UserVO
     */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/self")
    public ApiResponse<UserVO> self(@CurrentActor AuthenticatedActor actor) {
        // 协议翻译：会话本人 id → 查询用例 → 投影客户视图。不接受外部 userId，self-scope 由此从根保证。
        return ApiResponse.okData(UserVO.from(getSelfUserUseCase.getSelf(actor.userId())));
    }

    /**
     * 组装 session 会话凭据的 {@code Set-Cookie} 头串。
     *
     * <p>属性见 {@link #login} 文档：HttpOnly + Path=/ + SameSite=Lax，本地 http 不设 Secure。</p>
     *
     * @param token 已签发的会话令牌（JWT 紧凑串）
     * @return Set-Cookie 头值
     */
    private String buildSessionCookie(String token) {
        return SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax";
    }

    /**
     * 用户登出（F-1003）。
     *
     * <p>对齐 openapi.yaml {@code GET /api/user/logout}。本切片会话承载于无状态 JWT，
     * <b>服务端不维护会话表</b>，登出语义由客户端主动丢弃本地令牌实现，服务端只回执成功
     * （无会话亦幂等成功，符合 openapi「登出成功（无会话幂等）」）。</p>
     *
     * <p>注意（设计取舍）：无状态 JWT 下「服务端令牌黑名单」需引入额外存储（Redis）维护已撤销 jti
     * 直至自然过期，本切片不引入以保持最小切片；安全上依赖短 TTL 令牌 + 客户端丢弃。
     * TODO 后续 wave 若需「即时失效」语义，再在会话/网关层加 jti 黑名单。</p>
     *
     * @return 成功信封（无会话也幂等成功）
     */
    @GetMapping("/logout")
    public ApiResponse<Void> logout() {
        // 无状态 JWT：服务端无会话可清，客户端丢弃令牌即登出。此处只回执，保持幂等。
        return ApiResponse.ok("logout success");
    }

    /**
     * 提交重置新密码（F-1007）。
     *
     * <p>对齐 openapi.yaml {@code POST /api/user/reset}。协议翻译为 {@link ResetPasswordCommand}
     * 后调用用例；令牌校验/改密在应用层 + 领域层完成，本类不含业务逻辑。令牌无效/过期由
     * {@code GlobalExceptionHandler} 翻译为 400。</p>
     *
     * @param request 重置请求（已校验）
     * @return 成功信封（不回显任何敏感信息）
     */
    @PostMapping("/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordCommand command = new ResetPasswordCommand(
                request.email(), request.token(), request.password());
        resetPasswordUseCase.reset(command);
        return ApiResponse.ok("password reset success");
    }
}
