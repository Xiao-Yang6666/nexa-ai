package com.nexa.telegram.interfaces.api;

import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import com.nexa.telegram.application.TelegramBindCommand;
import com.nexa.telegram.application.TelegramBindUseCase;
import com.nexa.telegram.application.TelegramLoginCommand;
import com.nexa.telegram.application.TelegramLoginResult;
import com.nexa.telegram.application.TelegramLoginUseCase;
import com.nexa.telegram.interfaces.api.dto.ApiResponse;
import com.nexa.telegram.interfaces.api.dto.TelegramUserView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Telegram 登录/绑定控制器（接口层，F-1051/F-1052）。
 *
 * <p>承载 Telegram Login Widget 的两端点（区别于标准 OAuth 重定向回调，走 HMAC 校验）：
 * <ul>
 *   <li>{@code GET /api/oauth/telegram/login}（F-1051，security: []）→ {@link TelegramLoginUseCase}：
 *       HMAC 校验后登录/建号，返回客户视图 {@link TelegramUserView}（token 不进 body，产品铁律）。</li>
 *   <li>{@code GET /api/oauth/telegram/bind}（F-1052，sessionAuth）→ {@link TelegramBindUseCase}：
 *       已登录用户把当前 Telegram 账号绑到本账号，成功后 302 跳转 {@code /console/personal}。</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（backend-engineer §2.1）。HMAC 校验/唯一性护栏全在 domain/application，
 * 领域异常由 {@link TelegramExceptionHandler} 统一翻译。</p>
 *
 * <p><b>全参透传（安全要点）</b>：Telegram HMAC 校验要求对收到的<b>所有非 hash 字段</b>签名，故 login/bind
 * 用 {@code @RequestParam Map<String,String>} 接全部 query 参数透传给用例，由领域决定哪些参与签名——
 * 接口层<b>不</b>挑字段，否则攻击者可注入未签名字段绕过校验（F-1053）。绑定归属用户取自 {@code @CurrentActor}，
 * <b>不</b>从参数读，防伪造他人归属。</p>
 */
@RestController
@RequestMapping("/api/oauth/telegram")
public class TelegramController {

    /** 绑定成功后的跳转目标（openapi F-1052：302 跳转 /console/personal）。 */
    private static final String BIND_SUCCESS_REDIRECT = "/console/personal";

    private final TelegramLoginUseCase telegramLoginUseCase;
    private final TelegramBindUseCase telegramBindUseCase;

    /**
     * @param telegramLoginUseCase Telegram 登录/建号用例（F-1051）
     * @param telegramBindUseCase  Telegram 绑定用例（F-1052/F-1054）
     */
    public TelegramController(TelegramLoginUseCase telegramLoginUseCase,
                              TelegramBindUseCase telegramBindUseCase) {
        this.telegramLoginUseCase = telegramLoginUseCase;
        this.telegramBindUseCase = telegramBindUseCase;
    }

    /**
     * Telegram 登录（F-1051，{@code GET /api/oauth/telegram/login}，公开端点）。
     *
     * <p>HMAC 校验通过后：已绑定→登录；未绑定→建号+绑定。返回客户视图（无敏感字段）。
     * 校验失败/参数非法/未启用 → 领域异常 → 400（{@link TelegramExceptionHandler}）。</p>
     *
     * @param params Login Widget 回传的全部查询参数（含 id/hash/auth_date 等）
     * @return 成功信封，data = 登录用户客户视图
     */
    @GetMapping("/login")
    public ApiResponse<TelegramUserView> login(@RequestParam Map<String, String> params) {
        TelegramLoginResult result = telegramLoginUseCase.login(new TelegramLoginCommand(params));
        return ApiResponse.okData(TelegramUserView.from(result.user()));
    }

    /**
     * Telegram 绑定到现有账号（F-1052，{@code GET /api/oauth/telegram/bind}，需登录）。
     *
     * <p>HMAC 校验 + 唯一性校验（F-1054）通过后建绑定；成功 302 跳转 {@code /console/personal}
     * （对齐 openapi 302 响应）。归属用户取自 {@code @CurrentActor}（不读参数）。冲突/校验失败由
     * {@link TelegramExceptionHandler} 翻译（409/400）。</p>
     *
     * @param actor  认证主体（注入，提供会话用户 id）
     * @param params Login Widget 回传的全部查询参数
     * @return 302 重定向到个人中心（无 body）
     */
    @GetMapping("/bind")
    @RequireRole(AuthLevel.USER)
    public ResponseEntity<Void> bind(@CurrentActor AuthenticatedActor actor,
                                     @RequestParam Map<String, String> params) {
        telegramBindUseCase.bind(new TelegramBindCommand(params, actor.userId()));
        // 绑定成功：302 跳转个人中心（openapi F-1052 约定）。失败路径已由异常处理器拦截，不会走到这里。
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(BIND_SUCCESS_REDIRECT))
                .build();
    }
}
