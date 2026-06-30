package com.nexa.interfaces.api.modelgroup;

import com.nexa.application.modelgroup.ResolveAccessibleModelGroupsUseCase;
import com.nexa.interfaces.api.modelgroup.dto.UserModelGroupVO;
import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.rbac.AuthenticatedActor;
import com.nexa.common.security.annotation.CurrentActor;
import com.nexa.common.security.annotation.RequireRole;
import com.nexa.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户可选套餐分组控制器（USER 端点，创建 apikey 选分组用）。
 *
 * <p>套餐制落地的用户侧入口：创建 apikey 时只能绑定<b>有权限且存在</b>的分组。本控制器返回当前用户
 * 可选用的模型组（公开 + 该用户/令牌被授权的私有组，且启用 + 模型集非空），前端据此渲染下拉、
 * 后端创建时据此校验，杜绝绑定不存在/无权限的「孤儿分组」。</p>
 *
 * <p>鉴权：USER 级（sessionAuth），归属用户由 {@code @CurrentActor} 注入。</p>
 */
@RestController
@RequestMapping("/api/user/self/model_groups")
@RequireRole(AuthLevel.USER)
public class UserModelGroupController {

    private final ResolveAccessibleModelGroupsUseCase resolveUseCase;

    /**
     * @param resolveUseCase 可访问模型组解析用例（公开 + 已授权私有）
     */
    public UserModelGroupController(ResolveAccessibleModelGroupsUseCase resolveUseCase) {
        this.resolveUseCase = resolveUseCase;
    }

    /**
     * 列出当前用户可选用的套餐分组（创建 apikey 选分组）。
     *
     * <p>tokenId 传 0（会话维度，非令牌维度）——令牌级私有授权在建 key 时尚不存在，故只按 user 维 +
     * 公开组解析；autoLevelCodes 暂传 null（等级→code 映射未配置时不收窄）。</p>
     *
     * @param actor 认证主体（提供 userId）
     * @return 成功信封，data = 可选套餐分组列表（含 code/name/倍率/模型）
     */
    @GetMapping
    public ApiResponse<List<UserModelGroupVO>> listSelectable(@CurrentActor AuthenticatedActor actor) {
        List<UserModelGroupVO> views = resolveUseCase.resolve(actor.userId(), 0L, null).stream()
                .map(UserModelGroupVO::from)
                .toList();
        return ApiResponse.okData(views);
    }
}
