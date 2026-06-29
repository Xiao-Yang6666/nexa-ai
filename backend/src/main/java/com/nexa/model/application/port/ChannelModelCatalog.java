package com.nexa.model.application.port;

import java.util.List;
import java.util.Map;

/**
 * 渠道侧模型目录端口（应用层 / 防腐层接口，F-3021/F-3024/F-3025）。
 *
 * <p>模型广场/缺失检测需要读「渠道引用了哪些模型」，但渠道是 com.nexa.channel 的聚合，模型上下文
 * 不直接依赖另一 bounded context 的领域类（避免 context 间硬耦合，backend-engineer §2.5）。故在此
 * 定义模型上下文所需的最小只读能力端口，基础设施层用 channel 仓储实现，仅返回字符串/映射等
 * 弱类型契约（不泄露 Channel 领域对象）。</p>
 *
 * <p><b>客户视图铁律</b>：本端口仅输出对外模型名 A 与渠道→模型映射，绝不暴露上游模型 B/渠道 key/
 * 成本（产品三道闸之一）。</p>
 */
public interface ChannelModelCatalog {

    /**
     * 列出全部渠道引用到的模型名去重集合（F-3021 缺失检测输入）。
     *
     * @return 渠道 models 字段拆分去重后的模型名集合
     */
    List<String> referencedModelNames();

    /**
     * 渠道 id → 该渠道支持模型名列表 映射（F-3024 DashboardListModels）。
     *
     * @return channelId → models[]
     */
    Map<Long, List<String>> channelIdToModels();
}
