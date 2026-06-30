package com.nexa.interfaces.api.account;

import com.nexa.application.account.CreateUserByAdminCommand;
import com.nexa.application.account.CreateUserByAdminUseCase;
import com.nexa.application.account.ManageUserCommand;
import com.nexa.application.account.ManageUserUseCase;
import com.nexa.application.account.SearchUsersCommand;
import com.nexa.application.account.SearchUsersResult;
import com.nexa.application.account.SearchUsersUseCase;
import com.nexa.application.account.UpdateUserProfileCommand;
import com.nexa.application.account.UpdateUserProfileUseCase;
import com.nexa.domain.account.vo.Role;
import com.nexa.interfaces.api.account.dto.AdminBalanceAdjustRequest;
import com.nexa.interfaces.api.account.dto.AdminCreateUserRequest;
import com.nexa.interfaces.api.account.dto.AdminManageUserRequest;
import com.nexa.interfaces.api.account.dto.AdminUpdateUserRequest;
import com.nexa.interfaces.api.account.dto.AdminUserVO;
import com.nexa.interfaces.api.account.dto.BalanceTransactionVO;
import com.nexa.application.billing.AdminAdjustBalanceUseCase;
import com.nexa.common.web.ApiResponse;
import com.nexa.common.web.PageVO;
import com.nexa.common.security.domain.rbac.AuthLevel;
import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import com.nexa.common.security.interfaces.annotation.CurrentActor;
import com.nexa.common.security.interfaces.annotation.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 管理端用户管理控制器（AdminAuth 端点，接口层）。
 *
 * <p>承载用户管理块的管理端写/读端点（对齐 openapi.yaml /api/user 的 adminAuth operation）：
 * <ul>
 *   <li>{@code GET  /api/user/}        分页搜索/列表（F-1008）→ {@link SearchUsersUseCase}</li>
 *   <li>{@code POST /api/user/}        管理端创建用户（F-1009）→ {@link CreateUserByAdminUseCase}</li>
 *   <li>{@code POST /api/user/manage}  状态管理（F-1010）→ {@link ManageUserUseCase}</li>
 *   <li>{@code PUT  /api/user/}        更新资料（F-1011/1013/1014）→ {@link UpdateUserProfileUseCase}</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP DTO ⇄ 用例命令/结果），不含业务逻辑——角色越权护栏、
 * 状态机、资料校验全在 domain/application 内（backend-engineer §2.1）。出参一律用管理端视图
 * {@link AdminUserVO}（含 remark/inviter_id，但<b>绝不</b>含 password，产品铁律）。领域异常由
 * {@code GlobalExceptionHandler} 翻译为 HTTP 状态码（越权→403，目标不存在→404，参数非法→400）。</p>
 *
 * <p><b>鉴权（AdminAuth 真鉴权，已落地）</b>：本 controller 全部端点要求 {@code Role≥admin}——
 * 由类级 {@link RequireRole}{@code (ADMIN)} 方法级权限注解 + {@code SecurityConfig} 路径级
 * {@code hasAnyRole(ADMIN,ROOT)} 双重守护（F-5031）。操作者角色不再取自 {@code X-Operator-Role}
 * 而是由 {@code JwtAuthenticationFilter} 解析 JWT/会话后注入的认证主体
 * （{@code @CurrentActor AuthenticatedActor operator}）提供，杜绝客户端伪造操作者角色。
 * 越权（角色不足/管理层级护栏）→403，未认证→401。</p>
 */
@RestController
@RequestMapping("/api/user")
@RequireRole(AuthLevel.ADMIN)
public class AdminUserController {

    private final SearchUsersUseCase searchUsersUseCase;
    private final CreateUserByAdminUseCase createUserByAdminUseCase;
    private final ManageUserUseCase manageUserUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final AdminAdjustBalanceUseCase adminAdjustBalanceUseCase;

    /** $1 = 500000 quota（与前端 QUOTA_PER_USD 一致）。 */
    private static final BigDecimal QUOTA_PER_USD = BigDecimal.valueOf(500_000L);

    /**
     * @param searchUsersUseCase       用户搜索用例（F-1008）
     * @param createUserByAdminUseCase 管理端创建用户用例（F-1009）
     * @param manageUserUseCase        用户状态管理用例（F-1010）
     * @param updateUserProfileUseCase 更新用户资料用例（F-1011/1013/1014）
     * @param adminAdjustBalanceUseCase 管理员余额充值/扣费用例
     */
    public AdminUserController(SearchUsersUseCase searchUsersUseCase,
                               CreateUserByAdminUseCase createUserByAdminUseCase,
                               ManageUserUseCase manageUserUseCase,
                               UpdateUserProfileUseCase updateUserProfileUseCase,
                               AdminAdjustBalanceUseCase adminAdjustBalanceUseCase) {
        this.searchUsersUseCase = searchUsersUseCase;
        this.createUserByAdminUseCase = createUserByAdminUseCase;
        this.manageUserUseCase = manageUserUseCase;
        this.updateUserProfileUseCase = updateUserProfileUseCase;
        this.adminAdjustBalanceUseCase = adminAdjustBalanceUseCase;
    }

    /**
     * 用户分页搜索/列表（F-1008，对齐 openapi {@code GET /api/user/}）。
     *
     * <p>{@code keyword} 非空时按 username/email/group 模糊匹配（搜索），为空时返回全量分页（列表）。
     * 分页参数对齐 openapi {@code PageParam(p)} / {@code PageSizeParam(page_size)}；缺省页码 1、页大小 20。
     * 出参为 {@link AdminUserVO} 列表 + 分页元数据（{@link PageVO}）。</p>
     *
     * @param keyword  搜索关键词（可选，query {@code keyword}）
     * @param page     页码（可选，query {@code p}，缺省 1）
     * @param pageSize 每页条数（可选，query {@code page_size}，缺省 20）
     * @return 成功信封，data 为分页的管理端视图列表
     */
    // 同时映射 "" 与 "/"：契约路径为 /api/user/（带尾斜杠），但前端/调用方常以 /api/user（无尾斜杠）
    // 发起。Spring Boot 3 默认关闭尾斜杠匹配（PathPatternParser），单映射 "/" 时无尾斜杠请求无 handler →
    // MvcRequestMatcher 匹配不上 → 落 anyRequest().authenticated() 仍被 AuthorizationFilter 拒成 403。
    // 故两种形态都登记，保证 root 两种写法都进得来（鉴权不削弱：见 SecurityConfig 两路径均门槛 ADMIN+）。
    @GetMapping({"", "/"})
    public ApiResponse<PageVO<AdminUserVO>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "p", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize) {

        SearchUsersResult result = searchUsersUseCase.search(
                new SearchUsersCommand(keyword, page, pageSize));

        // 投影为管理端视图（passwordHash 在 AdminUserVO.from 中根本不读取，杜绝下发）。
        List<AdminUserVO> items = result.users().stream()
                .map(AdminUserVO::from)
                .toList();
        PageVO<AdminUserVO> data = new PageVO<>(
                items, result.total(), result.page(), result.pageSize());
        return ApiResponse.okData(data);
    }

    /**
     * 管理端创建用户（F-1009，对齐 openapi {@code POST /api/user/}）。
     *
     * <p>角色越权护栏（目标角色不可 ≥ 操作者角色）在聚合根 {@code User.createByAdmin} 内守护；
     * 用户名查重在用例内。成功仅回执 {@code SuccessResponse}（不下发新账号详情/密码，对齐 openapi
     * 200 = SuccessResponse）。</p>
     *
     * @param request  创建请求（已校验）
     * @param operator 认证主体（AdminAuth 注入，提供真实操作者角色）
     * @return 成功信封
     */
    @PostMapping("/")
    public ApiResponse<Void> create(
            @Valid @RequestBody AdminCreateUserRequest request,
            @CurrentActor AuthenticatedActor operator) {

        // role 缺省回落 common（避免 int 默认 0 被 Role.fromCode 当未知编码拒绝）。
        int roleCode = request.role() == null ? Role.COMMON.code() : request.role();
        CreateUserByAdminCommand command = new CreateUserByAdminCommand(
                request.username(),
                request.password(),
                null,                 // openapi 创建 schema 未含 email，本切片不接收（后续可扩展）
                request.displayName(),
                roleCode,
                operator.role().code());   // 操作者角色取自认证主体（非伪造请求头）
        createUserByAdminUseCase.create(command);
        return ApiResponse.ok("create user success");
    }

    /**
     * 用户状态管理（F-1010，对齐 openapi {@code POST /api/user/manage}）。
     *
     * <p>动作分发（enable/disable/promote/demote/delete）与角色越权护栏全在用例 + 聚合根内。
     * 越权抛 {@code RoleHierarchyViolationException}（→403），目标不存在抛 {@code UserNotFoundException}
     * （→404），未知 action 抛 {@code InvalidCredentialException}（→400）。成功回执 {@code SuccessResponse}。</p>
     *
     * @param request  管理请求（已校验）
     * @param operator 认证主体（AdminAuth 注入，提供真实操作者角色）
     * @return 成功信封
     */
    @PostMapping("/manage")
    public ApiResponse<Void> manage(
            @Valid @RequestBody AdminManageUserRequest request,
            @CurrentActor AuthenticatedActor operator) {

        ManageUserCommand command = new ManageUserCommand(
                request.id(), request.action(), operator.role().code());
        manageUserUseCase.manage(command);
        return ApiResponse.ok("manage user success");
    }

    /**
     * 更新用户资料（F-1011/1013/1014，对齐 openapi {@code PUT /api/user/}）。
     *
     * <p>部分更新语义（null 项不改）、各字段规范化/长度校验、角色护栏全在聚合根
     * {@code User.updateProfileByAdmin} 内守护。越权→403，目标不存在→404，字段非法→400。
     * 成功回执 {@code SuccessResponse}。</p>
     *
     * @param request  更新请求（已校验）
     * @param operator 认证主体（AdminAuth 注入，提供真实操作者角色）
     * @return 成功信封
     */
    @PutMapping("/")
    public ApiResponse<Void> update(
            @Valid @RequestBody AdminUpdateUserRequest request,
            @CurrentActor AuthenticatedActor operator) {

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                request.id(),
                request.displayName(),
                request.email(),
                request.group(),
                request.quota(),
                request.remark(),
                request.status(),
                request.discountRatio(),
                operator.role().code());
        updateUserProfileUseCase.update(command);
        return ApiResponse.ok("update user success");
    }

    /**
     * 管理员给用户充值（{@code POST /api/user/{id}/credit}）。
     *
     * @param id       目标用户 id
     * @param request  充值请求（amount USD &gt; 0）
     * @param operator 认证主体（提供操作者 id）
     * @return 成功信封，data = 充值后余额（USD）
     */
    @PostMapping("/{id}/credit")
    public ApiResponse<BigDecimal> credit(
            @PathVariable("id") long id,
            @RequestBody AdminBalanceAdjustRequest request,
            @CurrentActor AuthenticatedActor operator) {
        long quota = toQuota(request.amount());
        long after = adminAdjustBalanceUseCase
                .credit(id, quota, operator.userId(), request.remark()).value();
        return ApiResponse.okData(toUsd(after));
    }

    /**
     * 管理员给用户扣费（扣到 0 为止，{@code POST /api/user/{id}/debit}）。
     *
     * @param id       目标用户 id
     * @param request  扣费请求（amount USD &gt; 0）
     * @param operator 认证主体（提供操作者 id）
     * @return 成功信封，data = 扣费后余额（USD）
     */
    @PostMapping("/{id}/debit")
    public ApiResponse<BigDecimal> debit(
            @PathVariable("id") long id,
            @RequestBody AdminBalanceAdjustRequest request,
            @CurrentActor AuthenticatedActor operator) {
        long quota = toQuota(request.amount());
        long after = adminAdjustBalanceUseCase
                .debit(id, quota, operator.userId(), request.remark()).value();
        return ApiResponse.okData(toUsd(after));
    }

    /**
     * 查询用户账变流水（{@code GET /api/user/{id}/balance-logs}）。
     *
     * @param id    目标用户 id
     * @param limit 返回上限（缺省 50）
     * @return 成功信封，data = 账变流水列表（时间倒序）
     */
    @GetMapping("/{id}/balance-logs")
    public ApiResponse<List<BalanceTransactionVO>> balanceLogs(
            @PathVariable("id") long id,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
        List<BalanceTransactionVO> items = adminAdjustBalanceUseCase.logs(id, limit).stream()
                .map(BalanceTransactionVO::from)
                .toList();
        return ApiResponse.okData(items);
    }

    /** USD → quota（向下取整为整数 quota）。 */
    private static long toQuota(BigDecimal usd) {
        if (usd == null) {
            return 0L;
        }
        return usd.multiply(QUOTA_PER_USD).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** quota → USD（标度 6）。 */
    private static BigDecimal toUsd(long quota) {
        return BigDecimal.valueOf(quota).divide(QUOTA_PER_USD, 6, RoundingMode.HALF_UP);
    }
}
