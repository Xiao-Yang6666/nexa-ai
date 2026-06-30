package com.nexa.interfaces.api.model;

import com.nexa.application.model.ManageUserModelAliasUseCase;
import com.nexa.domain.model.model.UserModelAlias;
import com.nexa.domain.model.vo.AliasScopeType;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.model.dto.*;
import com.nexa.common.security.domain.rbac.AuthLevel;
import com.nexa.common.security.domain.rbac.AuthenticatedActor;
import com.nexa.common.security.interfaces.annotation.CurrentActor;
import com.nexa.common.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户层自助映射控制器（sessionAuth 端点，接口层，F-6003）。
 *
 * <p>承载本人/本组 C→A 映射全部端点（对齐 openapi/API-ENDPOINTS /api/user/self/model_aliases*）：
 * <ul>
 *   <li>{@code GET    /api/user/self/model_aliases}              列表（self-scope，本人+本组）</li>
 *   <li>{@code POST   /api/user/self/model_aliases}              创建（self-scope 强制 scope_id）</li>
 *   <li>{@code PUT    /api/user/self/model_aliases/{id}}         更新（self-scope 归属校验）</li>
 *   <li>{@code DELETE /api/user/self/model_aliases/{id}}         删除（self-scope 归属校验）</li>
 *   <li>{@code GET    /api/user/self/model_aliases/candidates}   候选联想（公开 A 全集，B 不可见闸）</li>
 * </ul>
 * </p>
 *
 * <p><b>越权护栏</b>（DB-SCHEMA §18 / ROLE-PERMISSION-MATRIX §3）：当前用户 id 经 {@link CurrentActor}
 * 注入，绝不信任入参 user_id / scope_id——强制服务端推导，防越权写他人/他组。</p>
 *
 * <p><b>B 不可见三道闸——候选层闸</b>：{@code /candidates} 只返公开 A 全集，绝不含任何 B。</p>
 */
@RestController
@RequestMapping("/api/user/self/model_aliases")
@RequireRole(AuthLevel.USER)
public class UserModelAliasController {

    private final ManageUserModelAliasUseCase useCase;

    /** @param useCase 自助映射 CRUD 用例 */
    public UserModelAliasController(ManageUserModelAliasUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 本人/本组映射列表（F-6003，{@code GET /api/user/self/model_aliases}）。
     *
     * @param actor 当前用户（注入，不信任入参）
     * @return 成功信封，data = items[]（UserVO，target 仅 A）
     */
    @GetMapping
    public ApiResponse<List<UserModelAliasUserVO>> list(@CurrentActor AuthenticatedActor actor) {
        List<UserModelAliasUserVO> items = useCase.listVisible(actor.userId()).stream()
                .map(UserModelAliasUserVO::from).toList();
        return ApiResponse.okData(items);
    }

    /**
     * 创建 C→A 映射（F-6003，{@code POST /api/user/self/model_aliases}）。
     *
     * @param actor   当前用户（注入；self-scope 强制 scope_id）
     * @param request 创建请求（scope_type/alias/target 必填）
     * @return 成功信封，data = 创建后映射（UserVO）
     */
    @PostMapping
    public ApiResponse<UserModelAliasUserVO> create(
            @CurrentActor AuthenticatedActor actor,
            @RequestBody UserModelAliasCreateRequest request) {
        AliasScopeType scopeType = AliasScopeType.fromCode(request.scopeType());
        UserModelAlias created = useCase.create(actor.userId(), scopeType, request.alias(),
                request.target(), request.enabled());
        return ApiResponse.okData(UserModelAliasUserVO.from(created));
    }

    /**
     * 更新 C→A 映射（F-6003，{@code PUT /api/user/self/model_aliases/{id}}）。
     *
     * @param actor   当前用户（注入；self-scope 归属校验）
     * @param id      path 映射 id
     * @param request 更新请求（target/enabled 可空）
     * @return 成功信封，data = 更新后映射（UserVO）
     */
    @PutMapping("/{id}")
    public ApiResponse<UserModelAliasUserVO> update(
            @CurrentActor AuthenticatedActor actor,
            @PathVariable("id") long id,
            @RequestBody UserModelAliasUpdateRequest request) {
        UserModelAlias updated = useCase.update(actor.userId(), id, request.target(), request.enabled());
        return ApiResponse.okData(UserModelAliasUserVO.from(updated));
    }

    /**
     * 删除 C→A 映射（F-6003，{@code DELETE /api/user/self/model_aliases/{id}}）。
     *
     * @param actor 当前用户（注入；self-scope 归属校验）
     * @param id    path 映射 id
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @CurrentActor AuthenticatedActor actor,
            @PathVariable("id") long id) {
        useCase.delete(actor.userId(), id);
        return ApiResponse.ok("alias deleted");
    }

    /**
     * 候选模型联想（F-6003，{@code GET /api/user/self/model_aliases/candidates}）。
     *
     * <p>来源 = PublicModel Enabled=true 全集（UserVO，绝不含任何 B——B 不可见三道闸之候选层闸）。
     * keyword 可空白，空白返回全集。落库不强制白名单（COMPAT §2 铁律）——候选只是前端联想提示。</p>
     *
     * @param actor   当前用户（注入，仅确认登录）
     * @param keyword query 关键词（可空）
     * @return 成功信封，data = 对外名 A 列表
     */
    @GetMapping("/candidates")
    public ApiResponse<List<String>> candidates(
            @CurrentActor AuthenticatedActor actor,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.okData(useCase.candidates(keyword));
    }
}
