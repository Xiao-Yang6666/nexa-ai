package com.nexa.application.ops.setup;

import com.nexa.application.ops.port.DatabaseTypeProvider;
import com.nexa.application.ops.port.RootUserProvisioner;
import com.nexa.domain.ops.setup.SetupRepository;
import com.nexa.domain.ops.setup.SetupStatus;
import org.springframework.stereotype.Service;

/**
 * 查询系统初始化状态用例（应用层，F-4015 GET /api/setup）。
 *
 * <p>编排：读初始化标记 → 已初始化返回 {@code status=true}；未初始化则补充 root 存在性 +
 * 数据库类型供引导前端决策（API-ENDPOINTS §9.1）。薄编排，无业务规则（规则在领域/端口）。</p>
 */
@Service
public class GetSetupStatusUseCase {

    private final SetupRepository setupRepository;
    private final RootUserProvisioner rootUserProvisioner;
    private final DatabaseTypeProvider databaseTypeProvider;

    /**
     * @param setupRepository      初始化标记仓储
     * @param rootUserProvisioner  root 用户开通端口（探测是否已有 root）
     * @param databaseTypeProvider 数据库类型端口
     */
    public GetSetupStatusUseCase(SetupRepository setupRepository,
                                 RootUserProvisioner rootUserProvisioner,
                                 DatabaseTypeProvider databaseTypeProvider) {
        this.setupRepository = setupRepository;
        this.rootUserProvisioner = rootUserProvisioner;
        this.databaseTypeProvider = databaseTypeProvider;
    }

    /**
     * 查询初始化状态（探测接口，恒成功）。
     *
     * @return 初始化状态
     */
    public SetupStatus execute() {
        if (setupRepository.isInitialized()) {
            return SetupStatus.completed();
        }
        return SetupStatus.pending(rootUserProvisioner.rootUserExists(), databaseTypeProvider.databaseType());
    }
}
