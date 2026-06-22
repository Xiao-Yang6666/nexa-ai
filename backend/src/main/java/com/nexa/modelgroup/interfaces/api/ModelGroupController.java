package com.nexa.modelgroup.interfaces.api;

import com.nexa.modelgroup.application.CreateModelGroupUseCase;
import com.nexa.modelgroup.application.DeleteModelGroupUseCase;
import com.nexa.modelgroup.application.ListModelGroupsUseCase;
import com.nexa.modelgroup.application.ManageModelGroupAccessUseCase;
import com.nexa.modelgroup.application.QueryUserModelGroupsUseCase;
import com.nexa.modelgroup.application.SetUserModelGroupsUseCase;
import com.nexa.modelgroup.application.UpdateModelGroupStatusUseCase;
import com.nexa.modelgroup.application.UpdateModelGroupUseCase;
import com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException;
import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.shared.web.ApiResponse;
import com.nexa.modelgroup.interfaces.api.dto.ModelGroupAccessRequest;
import com.nexa.modelgroup.interfaces.api.dto.ModelGroupAccessView;
import com.nexa.modelgroup.interfaces.api.dto.ModelGroupAdminView;
import com.nexa.modelgroup.interfaces.api.dto.ModelGroupCreateRequest;
import com.nexa.modelgroup.interfaces.api.dto.ModelGroupStatusRequest;
import com.nexa.modelgroup.interfaces.api.dto.ModelGroupUpdateRequest;
import com.nexa.modelgroup.interfaces.api.dto.SetUserModelGroupsRequest;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
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

import java.util.List;

/**
 * 模型组管理控制器（AdminAuth 端点，接口层）。
 *
 * <p>承载灵活模型组管理的管理端 CRUD + 访问授权端点：
 * <ul>
 *   <li>{@code GET    /api/admin/model_group}                 列表（可选 access_policy 过滤）</li>
 *   <li>{@code POST   /api/admin/model_group}                 创建（code 冲突校验）</li>
 *   <li>{@code PUT    /api/admin/model_group/{id}}            更新（部分更新，code 不可改）</li>
 *   <li>{@code PATCH  /api/admin/model_group/{id}/status}     启用/禁用</li>
 *   <li>{@code DELETE /api/admin/model_group/{id}}            软删除</li>
 *   <li>{@code GET    /api/admin/model_group/{id}/access}     授权清单</li>
 *   <li>{@code POST   /api/admin/model_group/{id}/access}     授权用户/令牌（私有组）</li>
 *   <li>{@code DELETE /api/admin/model_group/access/{accessId}} 撤销授权</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（HTTP DTO ⇄ 用例命令/聚合），不含业务逻辑。鉴权：类级
 * {@link RequireRole}{@code (ADMIN)}；领域异常由 {@link ModelGroupExceptionHandler} 翻译为 HTTP 状态码。</p>
 */
@RestController
@RequestMapping("/api/admin/model_group")
@RequireRole(AuthLevel.ADMIN)
public class ModelGroupController {

    private final ListModelGroupsUseCase listUseCase;
    private final CreateModelGroupUseCase createUseCase;
    private final UpdateModelGroupUseCase updateUseCase;
    private final UpdateModelGroupStatusUseCase statusUseCase;
    private final DeleteModelGroupUseCase deleteUseCase;
    private final ManageModelGroupAccessUseCase accessUseCase;
    private final QueryUserModelGroupsUseCase queryUserGroupsUseCase;
    private final SetUserModelGroupsUseCase setUserGroupsUseCase;

    /**
     * @param listUseCase            列表用例
     * @param createUseCase          创建用例
     * @param updateUseCase          更新用例
     * @param statusUseCase          状态切换用例
     * @param deleteUseCase          软删除用例
     * @param accessUseCase          访问授权用例
     * @param queryUserGroupsUseCase 查用户已授权组用例
     * @param setUserGroupsUseCase   覆盖式设置用户授权组用例
     */
    public ModelGroupController(ListModelGroupsUseCase listUseCase,
                                CreateModelGroupUseCase createUseCase,
                                UpdateModelGroupUseCase updateUseCase,
                                UpdateModelGroupStatusUseCase statusUseCase,
                                DeleteModelGroupUseCase deleteUseCase,
                                ManageModelGroupAccessUseCase accessUseCase,
                                QueryUserModelGroupsUseCase queryUserGroupsUseCase,
                                SetUserModelGroupsUseCase setUserGroupsUseCase) {
        this.listUseCase = listUseCase;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.statusUseCase = statusUseCase;
        this.deleteUseCase = deleteUseCase;
        this.accessUseCase = accessUseCase;
        this.queryUserGroupsUseCase = queryUserGroupsUseCase;
        this.setUserGroupsUseCase = setUserGroupsUseCase;
    }

    /**
     * 模型组列表。
     *
     * @param accessPolicy 可选访问策略过滤（缺省全部）
     * @return 成功信封，data 为管理视图列表
     */
    @GetMapping
    public ApiResponse<List<ModelGroupAdminView>> list(
            @RequestParam(name = "access_policy", required = false) String accessPolicy) {
        List<ModelGroupAdminView> views = listUseCase.list(accessPolicy).stream()
                .map(ModelGroupAdminView::from)
                .toList();
        return ApiResponse.okData(views);
    }

    /**
     * 创建模型组。
     *
     * @param request 创建请求
     * @return 成功信封，data 为创建后的管理视图
     */
    @PostMapping
    public ApiResponse<ModelGroupAdminView> create(@RequestBody ModelGroupCreateRequest request) {
        ModelGroup created = createUseCase.create(request.toCommand());
        return ApiResponse.okData(ModelGroupAdminView.from(created));
    }

    /**
     * 更新模型组（部分更新）。
     *
     * @param id      模型组主键（path）
     * @param request 更新请求
     * @return 成功信封，data 为更新后的管理视图
     */
    @PutMapping("/{id}")
    public ApiResponse<ModelGroupAdminView> update(@PathVariable("id") long id,
                                                   @RequestBody ModelGroupUpdateRequest request) {
        ModelGroup updated = updateUseCase.update(request.toCommand(id));
        return ApiResponse.okData(ModelGroupAdminView.from(updated));
    }

    /**
     * 切换启用/禁用状态。
     *
     * @param id      模型组主键（path）
     * @param request 状态请求（status 必填 1/2）
     * @return 成功信封，data 为更新后的管理视图
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<ModelGroupAdminView> updateStatus(@PathVariable("id") long id,
                                                         @RequestBody ModelGroupStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new InvalidModelGroupParameterException("status is required");
        }
        ModelGroup updated = statusUseCase.updateStatus(id, request.status());
        return ApiResponse.okData(ModelGroupAdminView.from(updated));
    }

    /**
     * 软删除模型组。
     *
     * @param id 模型组主键（path）
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        deleteUseCase.delete(id);
        return ApiResponse.ok("model group deleted");
    }

    /**
     * 查询某模型组的授权清单。
     *
     * @param id 模型组主键（path）
     * @return 成功信封，data 为授权记录视图列表
     */
    @GetMapping("/{id}/access")
    public ApiResponse<List<ModelGroupAccessView>> listAccess(@PathVariable("id") long id) {
        List<ModelGroupAccessView> views = accessUseCase.listAccess(id).stream()
                .map(ModelGroupAccessView::from)
                .toList();
        return ApiResponse.okData(views);
    }

    /**
     * 授权某用户/令牌访问私有模型组。
     *
     * @param id      模型组主键（path）
     * @param request 授权请求（subjectType + subjectId）
     * @return 成功信封，data 为授权记录视图
     */
    @PostMapping("/{id}/access")
    public ApiResponse<ModelGroupAccessView> grantAccess(@PathVariable("id") long id,
                                                         @RequestBody ModelGroupAccessRequest request) {
        if (request == null || request.subjectId() == null) {
            throw new InvalidModelGroupParameterException("subjectType and subjectId are required");
        }
        var granted = accessUseCase.grant(id, request.subjectType(), request.subjectId());
        return ApiResponse.okData(ModelGroupAccessView.from(granted));
    }

    /**
     * 撤销授权（按授权记录 id）。
     *
     * @param accessId 授权记录主键（path）
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/access/{accessId}")
    public ApiResponse<Void> revokeAccess(@PathVariable("accessId") long accessId) {
        accessUseCase.revoke(accessId);
        return ApiResponse.ok("access revoked");
    }

    /**
     * 查询某用户当前已被授权的私有模型组（用户列表/编辑弹窗回显）。
     *
     * @param userId 用户主键（path）
     * @return 成功信封，data 为该用户 USER 级授权命中的模型组视图列表
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<ModelGroupAdminView>> listUserGroups(@PathVariable("userId") long userId) {
        List<ModelGroupAdminView> views = queryUserGroupsUseCase.listForUser(userId).stream()
                .map(ModelGroupAdminView::from)
                .toList();
        return ApiResponse.okData(views);
    }

    /**
     * 覆盖式设置某用户的私有模型组授权（用户列表里勾选一批组后整体提交）。
     *
     * <p>请求体 {@code codes} 为该用户最终应拥有的全部组编码，后端做 diff 增删（空数组=清空）。
     * 某 code 无对应存活组 → 404。</p>
     *
     * @param userId  用户主键（path）
     * @param request 目标组编码集（覆盖式）
     * @return 成功信封，data 为设置后该用户授权命中的模型组视图列表
     */
    @PutMapping("/user/{userId}")
    public ApiResponse<List<ModelGroupAdminView>> setUserGroups(
            @PathVariable("userId") long userId,
            @RequestBody SetUserModelGroupsRequest request) {
        List<String> codes = request == null ? null : request.codes();
        List<ModelGroupAdminView> views = setUserGroupsUseCase.setForUser(userId, codes).stream()
                .map(ModelGroupAdminView::from)
                .toList();
        return ApiResponse.okData(views);
    }
}
