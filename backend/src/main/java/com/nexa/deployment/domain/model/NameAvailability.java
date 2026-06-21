package com.nexa.deployment.domain.model;

/**
 * 集群名称可用性结果（值对象，F-3053）。
 *
 * <p>承载「集群名称可用性查询」结果。对齐契约出参 {@code { available: bool, name }}
 * （API-ENDPOINTS §10.3）。原样回带查询的名称，便于前端对照（异步查询防错位）。</p>
 *
 * @param name      被查询的集群名称
 * @param available 该名称在 io.net 侧是否可用
 */
public record NameAvailability(String name, boolean available) {
}
