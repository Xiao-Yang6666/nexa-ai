package com.nexa.model.interfaces.api;

import com.nexa.model.application.ModelSquareUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型广场用户侧控制器（sessionAuth 端点，接口层，F-3024/F-3025）。
 *
 * <p>承载需登录的模型广场端点：
 * <ul>
 *   <li>{@code GET /api/user/self/models}      用户可见模型列表（F-3025，按用户分组聚合去重，仅 A）</li>
 *   <li>{@code GET /api/models/dashboard}      模型广场（F-3024，DashboardListModels：channelId→models）</li>
 * </ul>
 * </p>
 *
 * <p><b>鉴权（安全声明）</b>：类级 {@link RequireRole}({@link AuthLevel#USER})（sessionAuth）。
 * 与 AdminAuth 的 {@code ModelController} 分开，因鉴权级别不同（用户面 vs 管理面）。当前用户 id
 * 经 {@link CurrentActor} 注入，不信任入参 user_id（防越权）。</p>
 *
 * <p><b>客户视图铁律</b>：仅返回对外模型名 A，绝不含上游模型 B/成本/供应商（产品三道闸）。</p>
 */
@RestController
@RequireRole(AuthLevel.USER)
public class UserModelController {

    private final ModelSquareUseCase squareUseCase;

    /** @param squareUseCase 模型广场用例 */
    public UserModelController(ModelSquareUseCase squareUseCase) {
        this.squareUseCase = squareUseCase;
    }

    /**
     * 用户可见模型列表（F-3025，{@code GET /api/user/self/models}）。
     *
     * <p>按当前登录用户所属分组聚合该分组下启用渠道的模型，去重合并返回对外名 A。</p>
     *
     * @param actor 当前登录用户（注入，不信任入参 user_id）
     * @return 成功信封，data = 去重模型名数组（UserView，仅 A）
     */
    @GetMapping("/api/user/self/models")
    public ApiResponse<List<String>> visibleModels(@CurrentActor AuthenticatedActor actor) {
        return ApiResponse.okData(squareUseCase.visibleModels(actor.userId()));
    }

    /**
     * 模型广场（F-3024，{@code GET /api/models/dashboard}，渠道→模型映射）。
     *
     * @return 成功信封，data = channelId → models[] 映射（UserView）
     */
    @GetMapping("/api/models/dashboard")
    public ApiResponse<Map<Long, List<String>>> dashboard() {
        return ApiResponse.okData(squareUseCase.channelToModels());
    }
}
