package com.nexa.deployment.domain.model;

/**
 * 容器事件只读视图（io.net container event，F-3054）。
 *
 * <p>领域模型，零框架依赖。对齐契约 {@code events:[{ time, message }]}（API-ENDPOINTS §10.4）。</p>
 *
 * @param time    事件时间（上游原始字符串，可空）
 * @param message 事件描述（可空）
 */
public record ContainerEvent(String time, String message) {
}
