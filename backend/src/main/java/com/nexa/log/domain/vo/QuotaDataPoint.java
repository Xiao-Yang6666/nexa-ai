package com.nexa.log.domain.vo;

/**
 * 配额按日聚合数据项值对象（F-4007 管理端 / F-4008 按用户 / F-4009 自助）。
 *
 * <p>领域规则来源：prd 日志与用量 F-4007~F-4009「按日分组数据」。对齐 openapi QuotaDataItem：
 * {@code date / quota / count / model_name}。由仓储按 {@code (日期, model_name)} 分组聚合 logs 中
 * 的消费记录（Type=2）产出，读侧只读结构，无行为护栏（纯聚合 DTO 形态的值对象）。</p>
 *
 * @param date      日期（YYYY-MM-DD，按 created_at 落到自然日）
 * @param quota     当日该模型消费 quota 总和
 * @param count     当日该模型消费记录条数
 * @param modelName 模型名（按 model_name=C 口径聚合）
 */
public record QuotaDataPoint(String date, long quota, long count, String modelName) {
}
