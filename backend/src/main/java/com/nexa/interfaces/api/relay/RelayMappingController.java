package com.nexa.interfaces.api.relay;

import com.nexa.application.relay.ManageMappingUseCase;
import com.nexa.interfaces.web.ApiResponse;
import com.nexa.interfaces.api.relay.dto.UserAliasRequest;
import com.nexa.interfaces.api.relay.dto.UserAliasVO;
import com.nexa.domain.security.rbac.AuthLevel;
import com.nexa.domain.security.rbac.AuthenticatedActor;
import com.nexa.interfaces.security.annotation.CurrentActor;
import com.nexa.interfaces.security.annotation.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户层模型别名管理控制器（接口层，F-6011）。
 *
 * <p>承载 L1 别名（C→A）自助管理端点：
 * <ul>
 *   <li>{@code GET  /api/relay/aliases}           L1 别名列表（UserAuth，self-scope）</li>
 *   <li>{@code POST /api/relay/aliases}           L1 别名创建（UserAuth，self-scope）</li>
 *   <li>{@code DELETE /api/relay/aliases/{id}}    L1 别名删除（UserAuth，self-scope）</li>
 * </ul>
 * L2 全局底仓映射（A→B）已废弃——A→B 下沉为渠道级（{@code Channel.modelMapping}），由渠道管理端点维护，
 * 不再有 {@code /api/relay/mappings} 全局映射端点。</p>
 */
@RestController
@RequestMapping("/api/relay")
public class RelayMappingController {

    private final ManageMappingUseCase useCase;

    public RelayMappingController(ManageMappingUseCase useCase) {
        this.useCase = useCase;
    }

    /** L1 别名列表（UserAuth，self-scope）。 */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/aliases")
    public ResponseEntity<ApiResponse<List<UserAliasVO>>> listAliases(@CurrentActor AuthenticatedActor actor) {
        // self-scope: user 级作用域（自己的别名）
        com.nexa.domain.relay.vo.AliasScope scope =
                com.nexa.domain.relay.vo.AliasScope.user(actor.userId());
        List<UserAliasVO> views = useCase.listL1Aliases(scope).stream()
                .map(UserAliasVO::from).toList();
        return ResponseEntity.ok(ApiResponse.okData(views));
    }

    /** L1 别名创建（UserAuth，self-scope，scope 由 @CurrentActor 注入禁止越权）。 */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/aliases")
    public ResponseEntity<ApiResponse<Void>> createAlias(@RequestBody UserAliasRequest req,
                                                         @CurrentActor AuthenticatedActor actor) {
        com.nexa.domain.relay.vo.AliasScope scope =
                com.nexa.domain.relay.vo.AliasScope.user(actor.userId());
        useCase.createL1Alias(scope, req.alias(), req.target());
        return ResponseEntity.ok(ApiResponse.ok("alias created"));
    }

    /** L1 别名删除（UserAuth）。 */
    @RequireRole(AuthLevel.USER)
    @DeleteMapping("/aliases/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAlias(@PathVariable Long id) {
        useCase.deleteL1Alias(id);
        return ResponseEntity.ok(ApiResponse.ok("alias deleted"));
    }
}
