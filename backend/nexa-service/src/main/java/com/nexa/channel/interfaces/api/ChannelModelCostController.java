package com.nexa.channel.interfaces.api;

import com.nexa.channel.application.ManageChannelModelCostUseCase;
import com.nexa.channel.domain.model.ChannelModelCost;
import com.nexa.channel.domain.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.channel.interfaces.api.dto.ChannelModelCostAdminView;
import com.nexa.channel.interfaces.api.dto.ChannelModelCostListView;
import com.nexa.channel.interfaces.api.dto.ChannelModelCostWriteRequest;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商成本配置控制器（AdminAuth/RootAuth 端点，接口层，F-6006）。
 *
 * <p>承载成本倍率全部端点（对齐 openapi /api/channel_model_costs*）：
 * <ul>
 *   <li>{@code GET    /api/channel_model_costs}        成本列表（可选 channel_id/upstream_model 过滤）</li>
 *   <li>{@code POST   /api/channel_model_costs}        创建/更新成本（upsert，超管手填）</li>
 *   <li>{@code DELETE /api/channel_model_costs/{id}}   删除成本（删后视为缺失记 0+告警）</li>
 * </ul>
 * </p>
 *
 * <p><b>B 不可见三道闸——接口层闸</b>：响应 {@link ChannelModelCostAdminView}（含 cost_ratio + B），
 * 仅 admin/root；客户无任何读路径触达本控制器。类级 {@link RequireRole}({@link AuthLevel#ADMIN})。</p>
 */
@RestController
@RequestMapping("/api/channel_model_costs")
@RequireRole(AuthLevel.ADMIN)
public class ChannelModelCostController {

    private final ManageChannelModelCostUseCase useCase;

    /** @param useCase 成本配置 CRUD 用例 */
    public ChannelModelCostController(ManageChannelModelCostUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 成本倍率列表（F-6006，{@code GET /api/channel_model_costs}）。
     *
     * @param channelId     query 渠道 id 过滤（可空）
     * @param upstreamModel query 真实模型 B 过滤（可空）
     * @param page          query 页号（可空→1）
     * @param pageSize      query 每页条数（可空→10）
     * @return 成功信封，data = { items[], total }（AdminView，含 cost/B）
     */
    @GetMapping
    public ApiResponse<ChannelModelCostListView> list(
            @RequestParam(name = "channel_id", required = false) Integer channelId,
            @RequestParam(name = "upstream_model", required = false) String upstreamModel,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        Pagination pagination = Pagination.of(page, pageSize);
        List<ChannelModelCostAdminView> items = useCase.list(channelId, upstreamModel, pagination).stream()
                .map(ChannelModelCostAdminView::from).toList();
        return ApiResponse.okData(new ChannelModelCostListView(items, useCase.count(channelId, upstreamModel)));
    }

    /**
     * 创建/更新成本倍率（F-6006，{@code POST /api/channel_model_costs}，upsert）。
     *
     * @param request 写请求（channel_id / upstream_model 必填）
     * @return 成功信封，data = 落库后成本行（AdminView，含 cost/B）
     */
    @PostMapping
    public ApiResponse<ChannelModelCostAdminView> upsert(@RequestBody ChannelModelCostWriteRequest request) {
        ChannelModelCost saved = useCase.upsert(request.channelId(), request.upstreamModel(),
                request.costRatio(), request.completionCostRatio(), request.enabled(),
                null, request.remark());
        return ApiResponse.okData(ChannelModelCostAdminView.from(saved));
    }

    /**
     * 批量创建/更新成本倍率（按分组/账号批量设成本，{@code POST /api/channel_model_costs/batch}，upsert）。
     *
     * <p>请求体为成本写条目数组；逐条复用 (channel_id, upstream_model) 幂等 upsert，整批一个事务。
     * 前端"批量设成本倍率"把选中渠道 × 各自支持模型展开为条目传入。</p>
     *
     * @param requests 批量写请求（每条 channel_id / upstream_model 必填）
     * @return 成功信封，data = 落库后成本行数组（AdminView，含 cost/B）
     */
    @PostMapping("/batch")
    public ApiResponse<List<ChannelModelCostAdminView>> batchUpsert(
            @RequestBody List<ChannelModelCostWriteRequest> requests) {
        List<ManageChannelModelCostUseCase.BatchCostItem> items = requests.stream()
                .map(r -> new ManageChannelModelCostUseCase.BatchCostItem(
                        r.channelId(), r.upstreamModel(), r.costRatio(),
                        r.completionCostRatio(), r.enabled(), r.remark()))
                .toList();
        List<ChannelModelCostAdminView> views = useCase.batchUpsert(items).stream()
                .map(ChannelModelCostAdminView::from).toList();
        return ApiResponse.okData(views);
    }

    /**
     * 删除成本倍率（F-6006，{@code DELETE /api/channel_model_costs/{id}}）。
     *
     * @param id path 成本行 id
     * @return 成功信封（无 data）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        useCase.delete(id);
        return ApiResponse.ok("channel model cost deleted");
    }
}
