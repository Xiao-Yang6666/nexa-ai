package com.nexa.account.interfaces.api;

import com.nexa.account.application.GenerateOAuthStateCommand;
import com.nexa.account.application.GenerateOAuthStateUseCase;
import com.nexa.account.application.OAuthLoginCommand;
import com.nexa.account.application.OAuthLoginResult;
import com.nexa.account.application.OAuthLoginUseCase;
import com.nexa.account.domain.vo.OAuthProvider;
import com.nexa.shared.web.ApiResponse;
import com.nexa.account.interfaces.api.dto.UserView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 接口层控制器（state 生成 + 各 provider 登录/绑定回调，F-1015~1020）。
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP ⇄ 用例命令/结果），不含任何业务逻辑
 * （backend-engineer §2.1）。流程：
 * <ol>
 *   <li>从 query / path 取 provider/code/state/aff（协议级）；</li>
 *   <li>翻译为应用层命令（{@link GenerateOAuthStateCommand}/{@link OAuthLoginCommand}）；</li>
 *   <li>调用用例（{@link GenerateOAuthStateUseCase}/{@link OAuthLoginUseCase}）；</li>
 *   <li>把结果投影为出参 DTO（state 串 / 客户视图 {@link UserView} 零敏感字段）。</li>
 * </ol>
 * 领域/业务异常（state 无效、provider 未配置、绑定冲突等）由 {@link GlobalExceptionHandler}
 * 统一翻译为 HTTP 状态码 + 错误信封，本类不 try/catch 业务异常。</p>
 *
 * <p>对齐 openapi.yaml {@code /api/oauth/*}：
 * <ul>
 *   <li>{@code GET /api/oauth/state}（F-1015）：生成 CSRF state，{@code data} 为 state 串。</li>
 *   <li>{@code GET /api/oauth/{provider}}（F-1016）：通用回调（github/oidc 等），provider 为路径段，
 *       {@code data} 为 {@link UserView}。</li>
 *   <li>{@code GET /api/oauth/discord}（F-1018）：Discord 回调。</li>
 *   <li>{@code GET /api/oauth/linuxdo}（F-1020）：LinuxDO 回调。</li>
 * </ul>
 * 令牌（result.token()）不进 body（不下发 access_token，产品铁律，沿用 UserController 约定）。</p>
 *
 * <p>路由说明：Discord/LinuxDO 用<b>固定路径</b>显式声明（openapi 单列了这两条且为固定段），
 * 其余 provider（github/oidc）走通用 {@code /{provider}} 路径段。Spring 路由按「最精确匹配优先」，
 * 固定路径不会被通用变量路径吞掉，故 {@code /discord}、{@code /linuxdo}、{@code /state} 均优先命中
 * 各自方法，{@code /{provider}} 仅兜底其余 provider 标识。</p>
 */
@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    private final GenerateOAuthStateUseCase generateOAuthStateUseCase;
    private final OAuthLoginUseCase oauthLoginUseCase;

    /**
     * @param generateOAuthStateUseCase 生成 OAuth state 用例（F-1015）
     * @param oauthLoginUseCase         OAuth 登录/绑定用例（F-1016~1020）
     */
    public OAuthController(GenerateOAuthStateUseCase generateOAuthStateUseCase,
                           OAuthLoginUseCase oauthLoginUseCase) {
        this.generateOAuthStateUseCase = generateOAuthStateUseCase;
        this.oauthLoginUseCase = oauthLoginUseCase;
    }

    /**
     * 生成 OAuth state（CSRF）并暂存 aff（F-1015）。
     *
     * <p>对齐 openapi {@code GET /api/oauth/state}：返回 {@code data} 为 state 字符串，前端带它
     * 跳转第三方授权页，回调时原样带回供 CSRF 比对。</p>
     *
     * @param aff 发起授权时携带的邀请码（可选 query 参数）
     * @return 成功信封，data 为 state 串
     */
    @GetMapping("/state")
    public ApiResponse<String> state(@RequestParam(value = "aff", required = false) String aff) {
        String token = generateOAuthStateUseCase.generate(new GenerateOAuthStateCommand(aff));
        return ApiResponse.okData(token);
    }

    /**
     * Discord OAuth 登录/绑定回调（F-1018）。
     *
     * <p>对齐 openapi {@code GET /api/oauth/discord}（固定路径段，先于通用 {@code /{provider}} 匹配）。</p>
     *
     * @param code  第三方回调带回的授权码
     * @param state 回调带回的 state token（CSRF 校验）
     * @return 成功信封，data 为登录/建号后的用户客户视图
     */
    @GetMapping("/discord")
    public ApiResponse<UserView> discord(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state) {
        return handleCallback(OAuthProvider.DISCORD.code(), code, state);
    }

    /**
     * LinuxDO OAuth 登录/绑定回调（F-1020）。
     *
     * <p>对齐 openapi {@code GET /api/oauth/linuxdo}（固定路径段，先于通用 {@code /{provider}} 匹配）。
     * 信任级门槛校验留待后续 wave（见 {@code LinuxDoOAuthClient} TODO）。</p>
     *
     * @param code  第三方回调带回的授权码
     * @param state 回调带回的 state token（CSRF 校验）
     * @return 成功信封，data 为登录/建号后的用户客户视图
     */
    @GetMapping("/linuxdo")
    public ApiResponse<UserView> linuxdo(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state) {
        return handleCallback(OAuthProvider.LINUXDO.code(), code, state);
    }

    /**
     * 通用 OAuth 回调（github / oidc / 其余 provider，F-1016）。
     *
     * <p>对齐 openapi {@code GET /api/oauth/{provider}}：provider 为路径段。GitHub（F-1016/1017）与
     * 通用 OIDC（F-1019）经此路由（path 段分别为 {@code github}/{@code oidc}）。非法/未配置的 provider
     * 由用例/注册表抛 {@code InvalidCredentialException}，{@link GlobalExceptionHandler} 映射 400。</p>
     *
     * @param provider provider 路径段（github/oidc/...）
     * @param code     第三方回调带回的授权码
     * @param state    回调带回的 state token（CSRF 校验）
     * @return 成功信封，data 为登录/建号后的用户客户视图
     */
    @GetMapping("/{provider}")
    public ApiResponse<UserView> callback(@PathVariable("provider") String provider,
                                          @RequestParam(value = "code", required = false) String code,
                                          @RequestParam(value = "state", required = false) String state) {
        return handleCallback(provider, code, state);
    }

    /**
     * 回调公共编排：翻译为登录命令、调用用例、投影客户视图。
     *
     * <p>本切片回调统一走「登录/注册」语义（{@code bindUserId=null}）：未绑定则建号 + 绑定，
     * 已绑定则直接登录。绑定流程（已登录用户把第三方账号绑到本账号，需带认证身份）留待会话层接入后补齐。</p>
     *
     * @param provider provider 标识串
     * @param code     授权码
     * @param state    state token
     * @return 成功信封，data 为用户客户视图（token 不进 body）
     */
    private ApiResponse<UserView> handleCallback(String provider, String code, String state) {
        // 协议翻译：HTTP query/path → 应用层命令。bindUserId=null 表示登录/注册语义（非绑定）。
        OAuthLoginCommand command = new OAuthLoginCommand(provider, code, state, null);
        OAuthLoginResult result = oauthLoginUseCase.login(command);
        // 仅投影客户视图；result.token() 不放进 body（不下发 access_token，产品铁律）。
        return ApiResponse.okData(UserView.from(result.user()));
    }
}
