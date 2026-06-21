package com.nexa.compliance.domain.vo;

import com.nexa.compliance.domain.exception.InvalidRetentionPolicyException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * prompt/响应正文留存策略（值对象，不可变，按值相等）——F-5017 prompt 留存开关与保留期，DC-005。
 *
 * <p>充血值对象，封装两件事 + 其领域规则：
 * <ul>
 *   <li>是否留存请求/响应正文（{@code enabled}）——默认关（不留正文，仅落用量元数据，F-5008/DC-005）；</li>
 *   <li>开启留存时的保留期（{@code retentionDays}）——默认 ≤30 天，且不得超过法务硬上限。</li>
 * </ul>
 * 领域规则来源：API-ENDPOINTS §14.5 F-5017「正文留存默认关，可开且独立保留期默认 ≤30 天（DC-005）」、
 * Compliance 验收「留存开关与保留期可配且生效」。把开关与保留期的合法性、过期判定收敛到本值对象，
 * 避免散落在日志层/配置层的裸 boolean+int（充血，backend-engineer §2.2/§2.4）。</p>
 *
 * <p>配置承载：开关与天数经 Option（{@code com.nexa.ops}）持久化，键见
 * {@link com.nexa.compliance.domain.vo.ComplianceOptionKeys}；日志写入层据本策略决定是否落正文、
 * 归档/清理层据 {@link #isExpired} 判定超期（与 F-5015 日志归档联动）。</p>
 */
public final class PromptRetentionPolicy {

    /** 保留期默认值（天）——DC-005「默认 ≤30 天」取 30 为缺省。 */
    public static final int DEFAULT_RETENTION_DAYS = 30;

    /**
     * 保留期硬上限（天）——法务允许的最长正文留存。
     *
     * <p>取 180 天：超过此值的配置一律拒绝，防止「无限留存正文」违反 DC-005 最小化留存原则。
     * 该上限是领域硬约束，不随 Option 配置放宽（配置只能在 [0, 上限] 内调）。</p>
     */
    public static final int MAX_RETENTION_DAYS = 180;

    private final boolean enabled;
    private final int retentionDays;

    private PromptRetentionPolicy(boolean enabled, int retentionDays) {
        this.enabled = enabled;
        this.retentionDays = retentionDays;
    }

    /**
     * 默认策略：关闭正文留存（DC-005 默认不留正文）。
     *
     * @return 关闭留存的策略（retentionDays 取默认值，但因 enabled=false 不生效）
     */
    public static PromptRetentionPolicy disabled() {
        return new PromptRetentionPolicy(false, DEFAULT_RETENTION_DAYS);
    }

    /**
     * 开启正文留存并指定保留期。
     *
     * <p>领域规则：{@code retentionDays} 必须在 {@code [1, MAX_RETENTION_DAYS]} 内
     * （0 天等价于不留存，应改用 {@link #disabled()}；负数/超上限非法）。</p>
     *
     * @param retentionDays 保留天数
     * @return 开启留存的策略
     * @throws InvalidRetentionPolicyException 天数 &lt;=0 或超过 {@link #MAX_RETENTION_DAYS}
     */
    public static PromptRetentionPolicy enabledFor(int retentionDays) {
        if (retentionDays <= 0) {
            throw new InvalidRetentionPolicyException(
                    "retention days must be positive when retention is enabled, got " + retentionDays);
        }
        if (retentionDays > MAX_RETENTION_DAYS) {
            // 不吞错：超上限是配置违反最小化留存原则的信号，拒绝并提示上限。
            throw new InvalidRetentionPolicyException(
                    "retention days must be <= " + MAX_RETENTION_DAYS + ", got " + retentionDays);
        }
        return new PromptRetentionPolicy(true, retentionDays);
    }

    /**
     * 由配置（开关 + 天数）构造策略（Option 读取后的工厂）。
     *
     * <p>语义：{@code enabled=false} 时忽略天数返回 {@link #disabled()}；{@code enabled=true} 时
     * 走 {@link #enabledFor} 的天数校验。这是「配置 → 领域策略」的统一入口，应用层不自行拼装。</p>
     *
     * @param enabled       是否开启留存
     * @param retentionDays 保留天数（enabled=false 时忽略）
     * @return 留存策略
     * @throws InvalidRetentionPolicyException 开启时天数非法
     */
    public static PromptRetentionPolicy of(boolean enabled, int retentionDays) {
        return enabled ? enabledFor(retentionDays) : disabled();
    }

    /** @return 是否留存请求/响应正文 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return 保留天数（enabled=false 时该值不生效，仅为缺省记录） */
    public int retentionDays() {
        return retentionDays;
    }

    /**
     * 判断一条在 {@code recordedAt} 写入的正文是否已超期（应被归档/清理）。
     *
     * <p>领域规则：
     * <ul>
     *   <li>未开启留存（{@code enabled=false}）→ 任何正文都视为「不应存在」，恒返回超期 {@code true}
     *       （归档层据此清空遗留正文）；</li>
     *   <li>开启留存 → {@code now - recordedAt > retentionDays} 即超期。</li>
     * </ul>
     * 与 F-5015 日志归档/分区联动：归档层用本判定决定哪些正文搬冷存/清空。</p>
     *
     * @param recordedAt 正文写入时刻
     * @param now        当前时刻
     * @return 已超期返回 {@code true}
     */
    public boolean isExpired(Instant recordedAt, Instant now) {
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(now, "now");
        if (!enabled) {
            // 留存关闭：不应存在任何正文，遗留的一律判超期以便清理。
            return true;
        }
        Duration age = Duration.between(recordedAt, now);
        return age.compareTo(Duration.ofDays(retentionDays)) > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PromptRetentionPolicy other)) {
            return false;
        }
        return enabled == other.enabled && retentionDays == other.retentionDays;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, retentionDays);
    }

    @Override
    public String toString() {
        return enabled ? ("retain " + retentionDays + "d") : "no-retention";
    }
}
