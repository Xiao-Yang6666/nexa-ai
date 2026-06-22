package com.nexa.log.application;

import com.nexa.log.domain.model.LogEntry;
import com.nexa.log.domain.vo.Pagination;

import java.util.List;

/**
 * 日志分页查询结果（应用层载体；F-4001/F-4002 列表）。
 *
 * <p>承载「当前页领域对象 + 总数 + 分页参数」，供接口层裁剪为 AdminLogView/UserLogView 列表 +
 * total。放应用层（而非 domain）因它聚合了仓储分页结果与请求分页参数，是用例编排产物。</p>
 *
 * @param items      当前页日志领域对象
 * @param total      满足过滤条件的总条数
 * @param pagination 请求分页参数
 */
public record LogPage(List<LogEntry> items, long total, Pagination pagination) {
}
