package com.nexa.interfaces.log.api.dto;

import com.nexa.domain.log.vo.QuotaDataPoint;

/**
 * 配额按日数据项视图 DTO（接口层，F-4007/F-4008/F-4009）。
 *
 * <p>对齐 openapi components.schemas.QuotaDataItem：{@code date / quota / count / model_name}。
 * 管理端与自助共用同一结构（差异在 self-scope 过滤），均不含成本/利润字段（按日聚合用售价 quota）。</p>
 *
 * @param date      日期（YYYY-MM-DD）
 * @param quota     当日该模型消费 quota 总和
 * @param count     当日该模型消费条数
 * @param modelName 模型名（=C 口径）
 */
public record QuotaDataItemView(String date, long quota, long count, String modelName) {

    /**
     * 从领域配额数据点构造视图。
     *
     * @param point 配额数据点值对象
     * @return 配额数据项视图 DTO
     */
    public static QuotaDataItemView from(QuotaDataPoint point) {
        return new QuotaDataItemView(point.date(), point.quota(), point.count(), point.modelName());
    }
}
