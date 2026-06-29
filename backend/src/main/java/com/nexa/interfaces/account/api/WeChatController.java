package com.nexa.interfaces.account.api;

import com.nexa.application.account.InitWeChatAuthUseCase;
import com.nexa.application.account.OAuthLoginResult;
import com.nexa.application.account.WeChatLoginCommand;
import com.nexa.application.account.WeChatLoginUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.account.api.dto.UserView;
import com.nexa.interfaces.account.api.dto.WeChatAuthView;
import com.nexa.interfaces.account.api.dto.WeChatBindRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WeChat 扫码授权控制器（接口层，F-1021/1022）。
 *
 * <p>承载微信特有的「二维码发起 + 轮询/绑定」两端点（区别于标准 OAuth 重定向回调）：
 * <ul>
 *   <li>{@code GET  /api/oauth/wechat}（F-1021）→ {@link InitWeChatAuthUseCase}：判可用 + 发 state +
 *       返回前端拼二维码所需参数（{@link WeChatAuthView}，零敏感字段，不含 app_secret）。</li>
 *   <li>{@code POST /api/oauth/wechat/bind}（F-1022）→ {@link WeChatLoginUseCase}：用扫码授权码完成
 *       登录/建号/绑定，返回客户视图 {@link UserView}（token 不进 body，产品铁律）。</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（backend-engineer §2.1）。固定路径 {@code /wechat}、{@code /wechat/bind}
 * 比 {@code OAuthController} 的通用 {@code /{provider}} 更精确，Spring 按精确匹配优先命中本控制器，
 * 不会被通用回调吞掉。领域异常由 {@link GlobalExceptionHandler} 统一翻译（已含本控制器）。</p>
 *
 * <p>绑定语义（安全声明）：{@code /wechat/bind} 在 openapi 标 {@code security: []}（公开，登录/注册分支）；
 * 已登录用户的「绑定到本账号」分支需带认证身份（会话层接入后由认证主体填 {@code bindUserId}），本切片
 * 暂以 {@code bindUserId=null}（登录/注册语义）处理，绑定到具体用户留待会话层接入。</p>
 */
@RestController
@RequestMapping("/api/oauth/wechat")
public class WeChatController {

    private final InitWeChatAuthUseCase initWeChatAuthUseCase;
    private final WeChatLoginUseCase weChatLoginUseCase;

    /**
     * @param initWeChatAuthUseCase 微信发起态用例（F-1021）
     * @param weChatLoginUseCase    微信登录/绑定用例（F-1022）
     */
    public WeChatController(InitWeChatAuthUseCase initWeChatAuthUseCase,
                            WeChatLoginUseCase weChatLoginUseCase) {
        this.initWeChatAuthUseCase = initWeChatAuthUseCase;
        this.weChatLoginUseCase = weChatLoginUseCase;
    }

    /**
     * 微信扫码授权发起（F-1021，对齐 openapi {@code GET /api/oauth/wechat}）。
     *
     * @return 成功信封，data 为发起态（含可用标志 + 拼码参数 + state）
     */
    @GetMapping
    public ApiResponse<WeChatAuthView> auth() {
        InitWeChatAuthUseCase.WeChatAuthInitResult r = initWeChatAuthUseCase.init();
        return ApiResponse.okData(new WeChatAuthView(
                r.enabled(), r.appId(), r.scope(), r.redirectUri(), r.state()));
    }

    /**
     * 微信绑定/登录（F-1022，对齐 openapi {@code POST /api/oauth/wechat/bind}）。
     *
     * @param request 绑定请求（含授权码，已校验非空）
     * @return 成功信封，data 为登录/建号后的客户视图
     */
    @PostMapping("/bind")
    public ApiResponse<UserView> bind(@Valid @RequestBody WeChatBindRequest request) {
        // bindUserId=null：本切片走登录/注册语义；已登录绑定分支留待会话层接入填入认证用户 id。
        OAuthLoginResult result = weChatLoginUseCase.login(new WeChatLoginCommand(request.code(), null));
        return ApiResponse.okData(UserView.from(result.user()));
    }
}
