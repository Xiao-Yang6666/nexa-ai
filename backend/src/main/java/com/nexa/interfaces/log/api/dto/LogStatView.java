package com.nexa.interfaces.log.api.dto;

import com.nexa.domain.log.vo.LogStat;

/**
 * 日志统计视图 DTO（接口层，F-4004 管理端 / F-4005 自助）。
 *
 * <p>对齐 openapi components.schemas.LogStat：{@code quota / rpm / tpm}。仅 Type=2 消费口径
 * （由用例 asConsumeOnly 保证）。管理端与自助共用同一结构（差异在 self-scope 过滤，不在字段）。</p>
 *
 * @param quota 区间内消费 quota 总和
 * @param rpm   每分钟请求数
 * @param tpm   每分钟 token 数
 */
public record LogStatView(long quota, double rpm, double tpm) {

    /**
     * 从领域统计值对象构造视图。
     *
     * @param stat 统计值对象
     * @return 统计视图 DTO
     */
    public static LogStatView from(LogStat stat) {
        return new LogStatView(stat.quota(), stat.rpm(), stat.tpm());
    }
}
