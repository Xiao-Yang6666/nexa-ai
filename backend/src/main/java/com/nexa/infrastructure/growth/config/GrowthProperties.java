package com.nexa.infrastructure.growth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 增长子域返利/划转常量配置（基础设施层，对齐 PRD GR-4/GR-5 常量 + FC-026/FC-028）。
 *
 * <p>承载邀请返利分销的三个业务常量（原系统散落于 setting）：
 * <ul>
 *   <li>{@code quotaForInviter} 单次邀请返利额度（GR-4：邀请人 AffQuota/AffHistoryQuota += 此值）</li>
 *   <li>{@code quotaForNewUser} 被邀请人额外初始额度叠加（GR-4 I12，叠加在默认初始额度之上）</li>
 *   <li>{@code quotaPerUnit}    邀请额度划转最小单位（GR-5 T3：quota &gt;= 此值才允许划转）</li>
 * </ul>
 * 从 {@code growth.*} 前缀读取（{@code growth.quota-for-inviter} 等），给出与 PRD「默认值」一致的缺省，
 * 后续接入 §15 动态系统设置时只换数据来源、不动签名。</p>
 *
 * <p>backend-engineer §3.4 配置外置：业务常量不硬编码进领域/用例，由配置注入，可随运营调整。</p>
 */
@Component
@ConfigurationProperties(prefix = "growth")
public class GrowthProperties {

    /** 单次邀请返利额度（{@code QuotaForInviter}），缺省 0（运营按需开启返利金额）。 */
    private long quotaForInviter = 0L;

    /** 被邀请人额外初始额度叠加（{@code QuotaForNewUser}），缺省 0。 */
    private long quotaForNewUser = 0L;

    /** 邀请额度划转最小单位（{@code QuotaPerUnit}），缺省 1000（与常见额度精度一致，避免 0 导致任意小额划转）。 */
    private long quotaPerUnit = 1000L;

    /** @return 单次邀请返利额度 */
    public long getQuotaForInviter() {
        return quotaForInviter;
    }

    /** @param quotaForInviter 单次邀请返利额度（配置绑定注入） */
    public void setQuotaForInviter(long quotaForInviter) {
        this.quotaForInviter = quotaForInviter;
    }

    /** @return 被邀请人额外初始额度叠加 */
    public long getQuotaForNewUser() {
        return quotaForNewUser;
    }

    /** @param quotaForNewUser 被邀请人额外初始额度叠加（配置绑定注入） */
    public void setQuotaForNewUser(long quotaForNewUser) {
        this.quotaForNewUser = quotaForNewUser;
    }

    /** @return 邀请额度划转最小单位 */
    public long getQuotaPerUnit() {
        return quotaPerUnit;
    }

    /** @param quotaPerUnit 邀请额度划转最小单位（配置绑定注入） */
    public void setQuotaPerUnit(long quotaPerUnit) {
        this.quotaPerUnit = quotaPerUnit;
    }
}
