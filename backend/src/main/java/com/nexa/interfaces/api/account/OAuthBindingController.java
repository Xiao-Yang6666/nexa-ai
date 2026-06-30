package com.nexa.interfaces.api.account;

import com.nexa.application.account.ListUserOAuthBindingsUseCase;
import com.nexa.application.account.UnbindOAuthUseCase;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.account.dto.OAuthBindingVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * OAuth 绑定查询/解绑控制器（接口层，F-1026 本人解绑 / F-1027 管理端查询+解绑）。
 *
 * <p>承载 OAuth 绑定的「列出 + 解绑」端点（区别于 {@link OAuthController} 的登录/绑定回调）：
 * <ul>
 *   <li>{@code DELETE /api/user/self/oauth/bindings/{provider_id}}（F-1026，sessionAuth）
 *       → {@link UnbindOAuthUseCase#unbindSelf(long, long)}：本人解绑自定义 provider。</li>
 *   <li>{@code GET    /api/user/{id}/oauth/bindings}（F-1027，adminAuth）
 *       → {@link ListUserOAuthBindingsUseCase#list(long)}：管理端列出目标用户绑定。</li>
 *   <li>{@code DELETE /api/user/{id}/oauth/bindings/{provider_id}}（F-1027，adminAuth）
 *       → {@link UnbindOAuthUseCase#unbindByAdmin(long, long)}：管理端解绑目标用户绑定。</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP ⇄ 用例命令/结果），不含业务逻辑（backend-engineer §2.1）。
 * 归属护栏、目标存在性、绑定存在性等规则全在 domain/application 内。领域异常由 {@link GlobalExceptionHandler}
 * 翻译（绑定不存在/目标用户不存在 → 404）。出参用 {@link OAuthBindingVO}（敏感字段零泄露）。</p>
 *
 * <p>路由说明：{@code /self/...} 为固定段，比 {@code /{id}/...} 的变量段更精确，Spring 按精确匹配优先，
 * 本人端点不会被管理端变量路径吞掉。</p>
 *
 * <p><b>身份来源（安全声明，临时桩）</b>：本切片<b>尚未</b>落地会话/AdminAuth 鉴权过滤器
 * （token→身份解析在后续 wave）。本人解绑暂从请求头 {@code X-User-Id} 读取会话用户 id（缺失则 401 语义：
 * 本切片无头部则下游按非法用户 id 拒绝）。<b>这不是最终鉴权</b>——SecurityConfig 当前对非公开端点要求
 * {@code authenticated()}，过滤器接入后应由认证主体提供身份并移除本头部回退。在此之前这些端点不会无鉴权裸奔。</p>
 */
@RestController
@RequestMapping("/api/user")
public class OAuthBindingController {

    /** 会话用户 id 请求头（临时桩，待会话鉴权过滤器接入后由认证主体替代）。 */
    private static final String USER_ID_HEADER = "X-User-Id";

    private final ListUserOAuthBindingsUseCase listUserOAuthBindingsUseCase;
    private final UnbindOAuthUseCase unbindOAuthUseCase;

    /**
     * @param listUserOAuthBindingsUseCase 管理端绑定查询用例（F-1027）
     * @param unbindOAuthUseCase           解绑用例（F-1026/F-1027）
     */
    public OAuthBindingController(ListUserOAuthBindingsUseCase listUserOAuthBindingsUseCase,
                                  UnbindOAuthUseCase unbindOAuthUseCase) {
        this.listUserOAuthBindingsUseCase = listUserOAuthBindingsUseCase;
        this.unbindOAuthUseCase = unbindOAuthUseCase;
    }

    /**
     * 本人解绑自定义 OAuth provider 绑定（F-1026，对齐 openapi
     * {@code DELETE /api/user/self/oauth/bindings/{provider_id}}）。
     *
     * <p>仅能解绑会话用户本人名下绑定（用例按归属定位，查不到 → 404）。成功回执 {@code SuccessResponse}。</p>
     *
     * @param providerId 自定义 provider 整数主键（路径段 {@code {provider_id}}）
     * @param userId     会话用户 id（临时取自 {@code X-User-Id} 头）
     * @return 成功信封
     */
    @DeleteMapping("/self/oauth/bindings/{provider_id}")
    public ApiResponse<Void> unbindSelf(
            @PathVariable("provider_id") long providerId,
            @RequestHeader(name = USER_ID_HEADER) long userId) {
        unbindOAuthUseCase.unbindSelf(userId, providerId);
        return ApiResponse.ok("unbind oauth success");
    }

    /**
     * 管理端查询用户 OAuth 绑定（F-1027，对齐 openapi {@code GET /api/user/{id}/oauth/bindings}）。
     *
     * <p>出参为 {@code {items: OAuthBindingVO[]}}（对齐 openapi 200 data 结构）。目标用户不存在 → 404。</p>
     *
     * @param id 目标用户 id（路径段 {@code {id}}）
     * @return 成功信封，data 为 {@code {items: [...]}}
     */
    @GetMapping("/{id}/oauth/bindings")
    public ApiResponse<Map<String, List<OAuthBindingVO>>> listBindings(@PathVariable("id") long id) {
        List<OAuthBindingVO> items = listUserOAuthBindingsUseCase.list(id).stream()
                .map(OAuthBindingVO::from)
                .toList();
        // openapi data 为 { items: [...] } 对象包裹（非裸数组），用 Map 表达该单字段对象。
        return ApiResponse.okData(Map.of("items", items));
    }

    /**
     * 管理端解绑用户 OAuth 绑定（F-1027，对齐 openapi
     * {@code DELETE /api/user/{id}/oauth/bindings/{provider_id}}）。
     *
     * <p>目标用户不存在 → 404；目标用户在该 provider 下无绑定 → 404。成功回执 {@code SuccessResponse}。</p>
     *
     * @param id         目标用户 id（路径段 {@code {id}}）
     * @param providerId 自定义 provider 整数主键（路径段 {@code {provider_id}}）
     * @return 成功信封
     */
    @DeleteMapping("/{id}/oauth/bindings/{provider_id}")
    public ApiResponse<Void> unbindByAdmin(
            @PathVariable("id") long id,
            @PathVariable("provider_id") long providerId) {
        unbindOAuthUseCase.unbindByAdmin(id, providerId);
        return ApiResponse.ok("unbind oauth success");
    }
}
