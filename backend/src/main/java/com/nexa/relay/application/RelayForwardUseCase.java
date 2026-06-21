package com.nexa.relay.application;

import com.nexa.relay.domain.repository.PlatformModelMappingRepository;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.service.RelayPathResolver;
import com.nexa.relay.domain.service.TwoLayerModelResolver;
import com.nexa.relay.domain.vo.AliasScope;
import com.nexa.relay.domain.vo.ModelResolution;
import com.nexa.relay.domain.vo.RelayDispatch;
import org.springframework.stereotype.Service;

/**
 * Relay 中继转发用例（RL-1/RL-7 端到端编排：协议识别→两层映射→选渠→协议转换→调上游→计费→落 Log）。
 *
 * <p>应用层编排，薄壳无业务规则——业务全在 domain 层（映射/转换/计费/重试由各 domain service 完成）。
 * 本用例是 F-3026/F-3035/F-3060/F-3061 的入口。完整 HTTP client 转发需与 channel/routing/billing BC 集成，
 * 本期搭建骨架确保编译通过，后续 wave 注入跨 BC 端口与 HTTP client。</p>
 */
@Service
public class RelayForwardUseCase {

    private final PlatformModelMappingRepository l2Repo;
    private final UserModelAliasRepository l1Repo;
    private final RelayLogRepository logRepo;

    public RelayForwardUseCase(PlatformModelMappingRepository l2Repo,
                               UserModelAliasRepository l1Repo,
                               RelayLogRepository logRepo) {
        this.l2Repo = l2Repo;
        this.l1Repo = l1Repo;
        this.logRepo = logRepo;
    }

    /**
     * 解析请求路径分发（RL-2）。
     *
     * @param path HTTP 请求路径
     * @return 分发结果（mode + format）
     */
    public RelayDispatch resolveDispatch(String path) {
        return RelayPathResolver.resolve(path);
    }

    /**
     * 执行两层模型映射 C→A→B（RL-7 第②步）。
     *
     * @param requestedModel 客户输入名 C
     * @param userId         当前 userId（L1 user scope）
     * @param group          当前分组（L1 group scope）
     * @return 三段映射结果
     */
    public ModelResolution resolveModel(String requestedModel, long userId, String group) {
        AliasScope userScope = AliasScope.user(userId);
        AliasScope groupScope = AliasScope.group(group);
        return TwoLayerModelResolver.resolve(
                requestedModel,
                // L1 lookup: user > group 优先级
                alias -> l1Repo.findTargetByAlias(userScope, alias)
                        .orElseGet(() -> l1Repo.findTargetByAlias(groupScope, alias).orElse(null)),
                // L2 lookup
                publicName -> l2Repo.findUpstreamByPublicName(publicName).orElse(null)
        );
    }
}
