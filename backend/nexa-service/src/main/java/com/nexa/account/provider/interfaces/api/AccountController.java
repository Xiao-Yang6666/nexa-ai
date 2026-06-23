package com.nexa.account.provider.interfaces.api;

import com.nexa.account.provider.application.CreateAccountUseCase;
import com.nexa.account.provider.application.DeleteAccountUseCase;
import com.nexa.account.provider.application.GetAccountUseCase;
import com.nexa.account.provider.application.ListAccountsUseCase;
import com.nexa.account.provider.application.ToggleAccountUseCase;
import com.nexa.account.provider.application.UpdateAccountUseCase;
import com.nexa.account.provider.domain.vo.Pagination;
import com.nexa.account.provider.interfaces.api.dto.AccountCreateRequest;
import com.nexa.account.provider.interfaces.api.dto.AccountListView;
import com.nexa.account.provider.interfaces.api.dto.AccountUpdateRequest;
import com.nexa.account.provider.interfaces.api.dto.AccountView;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import com.nexa.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商账号管理控制器（AdminAuth 端点，接口层）。
 *
 * <p>承载账号管理 CRUD + 启停端点：
 * <ul>
 *   <li>{@code GET    /api/admin/accounts}             列表分页（按 platform 过滤）</li>
 *   <li>{@code POST   /api/admin/accounts}             创建账号</li>
 *   <li>{@code GET    /api/admin/accounts/{id}}        详情</li>
 *   <li>{@code PUT    /api/admin/accounts/{id}}        编辑（覆盖式）</li>
 *   <li>{@code DELETE /api/admin/accounts/{id}}        删除</li>
 *   <li>{@code PATCH  /api/admin/accounts/{id}/toggle} 启停</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），无业务逻辑。
 * 分页归一在 {@link Pagination}，字段校验/状态迁移在领域聚合（充血）。领域异常由
 * {@code ProviderAccountExceptionHandler} 统一翻译（400/404）。</p>
 *
 * <p><b>鉴权</b>：全 {@code /api/admin/accounts*} = AdminAuth。类级 {@link RequireRole}
 * ({@link AuthLevel#ADMIN})，由 {@code RequireRoleInterceptor} 拦截判定，未认证→401、越权→403。</p>
 *
 * <p><b>凭证安全铁律</b>：出参用 {@link AccountView}（绝不下发 credentials 原始凭证）。</p>
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequireRole(AuthLevel.ADMIN)
public class AccountController {

    private final ListAccountsUseCase listAccountsUseCase;
    private final GetAccountUseCase getAccountUseCase;
    private final CreateAccountUseCase createAccountUseCase;
    private final UpdateAccountUseCase updateAccountUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final ToggleAccountUseCase toggleAccountUseCase;

    /**
     * @param listAccountsUseCase  列表用例
     * @param getAccountUseCase    详情用例
     * @param createAccountUseCase 创建用例
     * @param updateAccountUseCase 编辑用例
     * @param deleteAccountUseCase 删除用例
     * @param toggleAccountUseCase 启停用例
     */
    public AccountController(ListAccountsUseCase listAccountsUseCase,
                            GetAccountUseCase getAccountUseCase,
                            CreateAccountUseCase createAccountUseCase,
                            UpdateAccountUseCase updateAccountUseCase,
                            DeleteAccountUseCase deleteAccountUseCase,
                            ToggleAccountUseCase toggleAccountUseCase) {
        this.listAccountsUseCase = listAccountsUseCase;
        this.getAccountUseCase = getAccountUseCase;
        this.createAccountUseCase = createAccountUseCase;
        this.updateAccountUseCase = updateAccountUseCase;
        this.deleteAccountUseCase = deleteAccountUseCase;
        this.toggleAccountUseCase = toggleAccountUseCase;
    }

    /**
     * 账号列表分页（{@code GET /api/admin/accounts}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @param platform query 平台过滤（可空）
     * @return 成功信封，data = { items[], total }（credentials 脱敏）
     */
    @GetMapping
    public ApiResponse<AccountListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "platform", required = false) String platform) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                AccountListView.from(listAccountsUseCase.list(platform, pagination)));
    }

    /**
     * 创建账号（{@code POST /api/admin/accounts}）。
     *
     * @param request 创建请求（name/platform/type 必填）
     * @return 成功信封，data = 创建后账号（AccountView）
     */
    @PostMapping
    public ApiResponse<AccountView> create(@RequestBody AccountCreateRequest request) {
        return ApiResponse.okData(
                AccountView.from(createAccountUseCase.create(request.toCommand())));
    }

    /**
     * 账号详情（{@code GET /api/admin/accounts/{id}}）。
     *
     * @param id path 账号 id
     * @return 成功信封，data = 账号（AccountView）
     */
    @GetMapping("/{id}")
    public ApiResponse<AccountView> get(@PathVariable("id") long id) {
        return ApiResponse.okData(AccountView.from(getAccountUseCase.get(id)));
    }

    /**
     * 编辑账号（{@code PUT /api/admin/accounts/{id}}，覆盖式）。
     *
     * @param id      path 账号 id
     * @param request 编辑请求（name/platform/type 必填）
     * @return 成功信封，data = 更新后账号（AccountView）
     */
    @PutMapping("/{id}")
    public ApiResponse<AccountView> update(@PathVariable("id") long id,
                                           @RequestBody AccountUpdateRequest request) {
        return ApiResponse.okData(
                AccountView.from(updateAccountUseCase.update(request.toCommand(id))));
    }

    /**
     * 删除账号（{@code DELETE /api/admin/accounts/{id}}）。
     *
     * @param id path 账号 id
     * @return 成功信封（删除成功）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        deleteAccountUseCase.delete(id);
        return ApiResponse.ok("account deleted");
    }

    /**
     * 启停账号（{@code PATCH /api/admin/accounts/{id}/toggle}）。
     *
     * @param id     path 账号 id
     * @param enable query 目标态（true=启用，false=禁用；缺省 true）
     * @return 成功信封，data = 更新后账号（AccountView）
     */
    @PatchMapping("/{id}/toggle")
    public ApiResponse<AccountView> toggle(
            @PathVariable("id") long id,
            @RequestParam(name = "enable", required = false, defaultValue = "true") boolean enable) {
        return ApiResponse.okData(AccountView.from(toggleAccountUseCase.toggle(id, enable)));
    }
}
