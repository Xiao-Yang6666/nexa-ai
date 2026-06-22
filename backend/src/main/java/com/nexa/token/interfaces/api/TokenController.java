package com.nexa.token.interfaces.api;

import com.nexa.token.application.BatchDeleteTokensUseCase;
import com.nexa.token.application.BatchRevealTokenKeysUseCase;
import com.nexa.token.application.CreateTokenUseCase;
import com.nexa.token.application.DeleteTokenUseCase;
import com.nexa.token.application.ListTokensUseCase;
import com.nexa.token.application.RevealTokenKeyUseCase;
import com.nexa.token.application.SearchTokensUseCase;
import com.nexa.token.application.UpdateTokenUseCase;
import com.nexa.token.domain.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.token.interfaces.api.dto.BatchIdsRequest;
import com.nexa.token.interfaces.api.dto.TokenCreateRequest;
import com.nexa.token.interfaces.api.dto.TokenListView;
import com.nexa.token.interfaces.api.dto.TokenUpdateRequest;
import com.nexa.token.interfaces.api.dto.TokenUserView;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 令牌管理控制器（sessionAuth 端点，接口层，F-3001~F-3011）。
 *
 * <p>承载令牌 CRUD/搜索/批量/取明文 key 全部端点（对齐 openapi /api/token*）：
 * <ul>
 *   <li>{@code GET    /api/token/}            令牌列表分页（F-3002）</li>
 *   <li>{@code POST   /api/token/}            创建令牌（F-3001）</li>
 *   <li>{@code PUT    /api/token/}            更新令牌（F-3006，含 status_only 分支 + F-3008~3012）</li>
 *   <li>{@code GET    /api/token/search}      令牌搜索（F-3003）</li>
 *   <li>{@code DELETE /api/token/{id}}        删除单个令牌（F-3007，软删）</li>
 *   <li>{@code POST   /api/token/batch}       批量删除令牌（F-3007，软删）</li>
 *   <li>{@code POST   /api/token/{id}/key}    获取单个令牌明文 key（F-3004，受控）</li>
 *   <li>{@code POST   /api/token/keys/batch}  批量获取明文 key（F-3005，上限 100，受控）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），无业务逻辑。
 * 分页归一在 {@link Pagination}，字段校验/状态迁移/护栏在领域聚合（充血）。领域异常由
 * {@code TokenExceptionHandler} 统一翻译（400/403/404）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/token*} = sessionAuth + USER 级。本控制器类级
 * {@link RequireRole}({@link AuthLevel#USER})，由 {@code RequireRoleInterceptor} 统一拦截判定，
 * 未认证→401。归属用户由 {@code @CurrentActor} 注入，杜绝从请求体读 user_id 伪造他人归属。</p>
 *
 * <p><b>客户视图铁律</b>：列表/搜索/创建/更新出参用 {@link TokenUserView}（key MaskTokenKey 脱敏，
 * 绝不下发成本/上游字段）。明文 key 仅 F-3004/F-3005 受控端点下发，且仅本人令牌（用例级 self-scope）。</p>
 */
@RestController
@RequestMapping("/api/token")
@RequireRole(AuthLevel.USER)
public class TokenController {

    private final ListTokensUseCase listTokensUseCase;
    private final SearchTokensUseCase searchTokensUseCase;
    private final CreateTokenUseCase createTokenUseCase;
    private final UpdateTokenUseCase updateTokenUseCase;
    private final DeleteTokenUseCase deleteTokenUseCase;
    private final BatchDeleteTokensUseCase batchDeleteTokensUseCase;
    private final RevealTokenKeyUseCase revealTokenKeyUseCase;
    private final BatchRevealTokenKeysUseCase batchRevealTokenKeysUseCase;

    /**
     * @param listTokensUseCase           列表用例（F-3002）
     * @param searchTokensUseCase         搜索用例（F-3003）
     * @param createTokenUseCase          创建用例（F-3001）
     * @param updateTokenUseCase          更新用例（F-3006）
     * @param deleteTokenUseCase          单删用例（F-3007）
     * @param batchDeleteTokensUseCase    批量删除用例（F-3007）
     * @param revealTokenKeyUseCase       单取明文 key 用例（F-3004）
     * @param batchRevealTokenKeysUseCase 批量取明文 key 用例（F-3005）
     */
    public TokenController(ListTokensUseCase listTokensUseCase,
                           SearchTokensUseCase searchTokensUseCase,
                           CreateTokenUseCase createTokenUseCase,
                           UpdateTokenUseCase updateTokenUseCase,
                           DeleteTokenUseCase deleteTokenUseCase,
                           BatchDeleteTokensUseCase batchDeleteTokensUseCase,
                           RevealTokenKeyUseCase revealTokenKeyUseCase,
                           BatchRevealTokenKeysUseCase batchRevealTokenKeysUseCase) {
        this.listTokensUseCase = listTokensUseCase;
        this.searchTokensUseCase = searchTokensUseCase;
        this.createTokenUseCase = createTokenUseCase;
        this.updateTokenUseCase = updateTokenUseCase;
        this.deleteTokenUseCase = deleteTokenUseCase;
        this.batchDeleteTokensUseCase = batchDeleteTokensUseCase;
        this.revealTokenKeyUseCase = revealTokenKeyUseCase;
        this.batchRevealTokenKeysUseCase = batchRevealTokenKeysUseCase;
    }

    /**
     * 令牌列表分页（F-3002，{@code GET /api/token/}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @param actor    认证主体（self-scope，决定 user_id 过滤）
     * @return 成功信封，data = { items[], total, page, pageSize }（UserView，key 脱敏）
     */
    @GetMapping("/")
    public ApiResponse<TokenListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @CurrentActor AuthenticatedActor actor) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                TokenListView.from(listTokensUseCase.list(actor.userId(), pagination)));
    }

    /**
     * 创建令牌（F-3001，{@code POST /api/token/}）。
     *
     * @param request 创建请求（name 必填）
     * @param actor   认证主体（提供归属用户 id）
     * @return 成功信封，data = 创建后令牌（UserView，key 脱敏）
     */
    @PostMapping("/")
    public ApiResponse<TokenUserView> create(
            @RequestBody TokenCreateRequest request,
            @CurrentActor AuthenticatedActor actor) {

        return ApiResponse.okData(
                TokenUserView.from(createTokenUseCase.create(request.toCommand(actor.userId()))));
    }

    /**
     * 更新令牌（F-3006，{@code PUT /api/token/}，含 status_only 分支）。
     *
     * @param request 更新请求（id 必填，可选 status_only）
     * @param actor   认证主体（self-scope 校验）
     * @return 成功信封，data = 更新后令牌（UserView）
     */
    @PutMapping("/")
    public ApiResponse<TokenUserView> update(
            @RequestBody TokenUpdateRequest request,
            @CurrentActor AuthenticatedActor actor) {

        return ApiResponse.okData(
                TokenUserView.from(updateTokenUseCase.update(request.toCommand(actor.userId()))));
    }

    /**
     * 令牌搜索（F-3003，{@code GET /api/token/search}）。
     *
     * @param keyword  query 关键词（可空白→该用户全量）
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @param actor    认证主体（self-scope，决定 user_id 过滤）
     * @return 成功信封，data = { items[], total, page, pageSize }（UserView）
     */
    @GetMapping("/search")
    public ApiResponse<TokenListView> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @CurrentActor AuthenticatedActor actor) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                TokenListView.from(searchTokensUseCase.search(actor.userId(), keyword, pagination)));
    }

    /**
     * 删除单个令牌（F-3007，{@code DELETE /api/token/{id}}，软删）。
     *
     * @param id    path 令牌 id
     * @param actor 认证主体（self-scope）
     * @return 成功信封（删除成功）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") long id,
            @CurrentActor AuthenticatedActor actor) {

        deleteTokenUseCase.delete(actor.userId(), id);
        return ApiResponse.ok("token deleted");
    }

    /**
     * 批量删除令牌（F-3007，{@code POST /api/token/batch}，软删）。
     *
     * @param request 批量请求（ids）
     * @param actor   认证主体（self-scope，仅删本人令牌）
     * @return 成功信封，data = 实际删除条数
     */
    @PostMapping("/batch")
    public ApiResponse<Integer> batchDelete(
            @RequestBody BatchIdsRequest request,
            @CurrentActor AuthenticatedActor actor) {

        return ApiResponse.okData(
                batchDeleteTokensUseCase.batchDelete(actor.userId(), request.ids()));
    }

    /**
     * 获取单个令牌明文 key（F-3004，{@code POST /api/token/{id}/key}，受控）。
     *
     * <p><b>安全</b>：仅本人令牌可取明文 key（self-scope，越权 → 403）。明文 key 直接作为 data 字符串
     * 返回（对齐 openapi {@code data: string}）。</p>
     *
     * @param id    path 令牌 id
     * @param actor 认证主体
     * @return 成功信封，data = 完整明文 key
     */
    @PostMapping("/{id}/key")
    public ApiResponse<String> revealKey(
            @PathVariable("id") long id,
            @CurrentActor AuthenticatedActor actor) {

        return ApiResponse.okData(revealTokenKeyUseCase.reveal(actor.userId(), id));
    }

    /**
     * 批量获取明文 key（F-3005，{@code POST /api/token/keys/batch}，上限 100，受控）。
     *
     * @param request 批量请求（ids，≤100）
     * @param actor   认证主体（self-scope，仅返回归属本人的令牌）
     * @return 成功信封，data = id→key 映射
     */
    @PostMapping("/keys/batch")
    public ApiResponse<Map<Long, String>> batchRevealKeys(
            @RequestBody BatchIdsRequest request,
            @CurrentActor AuthenticatedActor actor) {

        return ApiResponse.okData(
                batchRevealTokenKeysUseCase.batchReveal(actor.userId(), request.ids()));
    }
}
