package com.nexa.infrastructure.model.catalog;

import com.nexa.domain.account.provider.model.Account;
import com.nexa.domain.account.provider.repository.AccountRepository;
import com.nexa.application.model.port.ChannelModelCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型目录端口的实现（基础设施层适配器，F-3021/F-3024/F-3025）。
 *
 * <p>跨 bounded context 只读适配：模型上下文经 {@link ChannelModelCatalog} 端口声明所需的「供应侧
 * 模型信息」，本 adapter 用 {@code com.nexa.account} 的领域仓储 {@link AccountRepository} 实现，
 * 仅把 Account 聚合「投影」成字符串/映射弱契约返回，不让 Account 领域对象渗出端口（context 间防腐，
 * backend-engineer §2.5）。供应账号↔模型是两个上下文，这里是允许的「上下文间集成」点。</p>
 *
 * <p>历史：转发体系已从旧 channel 迁移到 account，本 adapter 的数据源随之从 {@code ChannelRepository}
 * 改为 {@link AccountRepository}（端口契约不变，调用方 {@code ModelSquareUseCase}/{@code DetectMissingModelsUseCase}
 * 无需改动）。端口方法名中的 channelId 现承载 accountId（弱契约保持向后兼容）。</p>
 */
@Component
public class ChannelModelCatalogAdapter implements ChannelModelCatalog {

    private final AccountRepository accountRepository;

    /** @param accountRepository 供应商账号领域仓储（跨上下文只读集成） */
    public ChannelModelCatalogAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> referencedModelNames() {
        // 全部账号引用的模型名去重（保序）。
        Set<String> names = new LinkedHashSet<>();
        for (Account a : accountRepository.findAll()) {
            names.addAll(splitModels(a.models()));
        }
        return new ArrayList<>(names);
    }

    /** {@inheritDoc} */
    @Override
    public Map<Long, List<String>> channelIdToModels() {
        // 账号 id → 支持模型名列表（F-3024 DashboardListModels；键为 accountId，弱契约沿用旧方法名）。
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Account a : accountRepository.findAll()) {
            if (a.id() != null) {
                result.put(a.id(), splitModels(a.models()));
            }
        }
        return result;
    }

    /**
     * 账号 models 字段（逗号分隔串）拆分为去空白去空模型名列表（保序）。
     *
     * @param models models 字段
     * @return 模型名列表
     */
    private List<String> splitModels(String models) {
        if (models == null || models.isBlank()) {
            return List.of();
        }
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
