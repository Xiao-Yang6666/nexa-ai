package com.nexa.model.infrastructure.sync;

import com.nexa.model.application.port.UpstreamModelCatalog;
import com.nexa.model.domain.vo.UpstreamModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上游模型目录端口的占位实现（基础设施层 stub adapter，F-3019/F-3020）。
 *
 * <p><b>切片边界说明（诚实标注）</b>：本片（W2 模型元数据/同步）聚焦模型/供应商 CRUD、同步比对计划
 * （领域服务 {@link com.nexa.model.domain.service.ModelSyncPlanner}）与落地编排的完整领域与持久化。
 * 真实上游交互（按 locale 选 basellm 元数据 URL、ETag/304 bodyCache、HTTP 拉取 + JSON 解析）涉及
 * 网络 IO 与上游格式适配，按「功能切小、优先保证编译通过」原则，本类先返回空集占位：</p>
 * <ul>
 *   <li>返回空 → 上游无新增（{@code execute} 据此走「不请求上游直接返回零计数」，BACKLOG T-121 验收路径）。</li>
 * </ul>
 *
 * <p>真实接入时仅替换本 adapter（按 locale 选址、发 HTTP、解析成 {@link UpstreamModel} 列表），
 * 应用层 {@code SyncUpstreamModelsUseCase} / 领域服务 {@code ModelSyncPlanner} 无需改动
 * （DDD 防腐层价值，backend-engineer §2.3）。真实失败路径应抛
 * {@code UpstreamSyncException}（端口契约已声明，接口层映射 502）。</p>
 */
@Component
public class StubUpstreamModelCatalog implements UpstreamModelCatalog {

    /** {@inheritDoc} */
    @Override
    public List<UpstreamModel> fetch(String locale) {
        // 占位：返回空集（上游无新增）。真实现按 locale 选 basellm URL，HTTP 拉取 + 解析。
        return List.of();
    }
}
