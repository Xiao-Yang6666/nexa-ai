package com.nexa.relay.interfaces.api;

import com.nexa.relay.application.ManageMappingUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.relay.interfaces.api.dto.PlatformMappingRequest;
import com.nexa.relay.interfaces.api.dto.PlatformMappingView;
import com.nexa.relay.interfaces.api.dto.UserAliasRequest;
import com.nexa.relay.interfaces.api.dto.UserAliasView;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
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
 * 两层模型映射管理控制器（接口层，F-6011）。
 *
 * <p>承载映射管理端点：
 * <ul>
 *   <li>{@code GET  /api/relay/mappings}         L2 底仓列表（AdminAuth）</li>
 *   <li>{@code POST /api/relay/mappings}          L2 底仓创建（RootAuth，B 只有 root 可配）</li>
 *   <li>{@code DELETE /api/relay/mappings/{id}}   L2 底仓删除（RootAuth）</li>
 *   <li>{@code GET  /api/relay/aliases}           L1 别名列表（UserAuth，self-scope）</li>
 *   <li>{@code POST /api/relay/aliases}           L1 别名创建（UserAuth，self-scope）</li>
 *   <li>{@code DELETE /api/relay/aliases/{id}}    L1 别名删除（UserAuth，self-scope）</li>
 * </ul>
 * 可见性铁律：L2 底仓视图 {@link PlatformMappingView} 含 B，只在 AdminAuth/RootAuth 路由返回（不出现在任何 user 路由）。</p>
 */
@RestController
@RequestMapping("/api/relay")
public class RelayMappingController {

    private final ManageMappingUseCase useCase;

    public RelayMappingController(ManageMappingUseCase useCase) {
        this.useCase = useCase;
    }

    /** L2 底仓映射列表（AdminAuth）。 */
    @RequireRole(AuthLevel.ADMIN)
    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<List<PlatformMappingView>>> listMappings() {
        List<PlatformMappingView> views = useCase.listL2Mappings().stream()
                .map(PlatformMappingView::from).toList();
        return ResponseEntity.ok(ApiResponse.okData(views));
    }

    /** L2 底仓映射创建（RootAuth，B 只有 root 可配）。 */
    @RequireRole(AuthLevel.ROOT)
    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<Void>> createMapping(@RequestBody PlatformMappingRequest req) {
        useCase.createL2Mapping(req.publicName(), req.upstreamName(), req.remark());
        return ResponseEntity.ok(ApiResponse.ok("mapping created"));
    }

    /** L2 底仓映射删除（RootAuth）。 */
    @RequireRole(AuthLevel.ROOT)
    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable Long id) {
        useCase.deleteL2Mapping(id);
        return ResponseEntity.ok(ApiResponse.ok("mapping deleted"));
    }

    /** L1 别名列表（UserAuth，self-scope）。 */
    @RequireRole(AuthLevel.USER)
    @GetMapping("/aliases")
    public ResponseEntity<ApiResponse<List<UserAliasView>>> listAliases(@CurrentActor AuthenticatedActor actor) {
        // self-scope: user 级作用域（自己的别名）
        com.nexa.relay.domain.vo.AliasScope scope =
                com.nexa.relay.domain.vo.AliasScope.user(actor.userId());
        List<UserAliasView> views = useCase.listL1Aliases(scope).stream()
                .map(UserAliasView::from).toList();
        return ResponseEntity.ok(ApiResponse.okData(views));
    }

    /** L1 别名创建（UserAuth，self-scope，scope 由 @CurrentActor 注入禁止越权）。 */
    @RequireRole(AuthLevel.USER)
    @PostMapping("/aliases")
    public ResponseEntity<ApiResponse<Void>> createAlias(@RequestBody UserAliasRequest req,
                                                         @CurrentActor AuthenticatedActor actor) {
        com.nexa.relay.domain.vo.AliasScope scope =
                com.nexa.relay.domain.vo.AliasScope.user(actor.userId());
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
