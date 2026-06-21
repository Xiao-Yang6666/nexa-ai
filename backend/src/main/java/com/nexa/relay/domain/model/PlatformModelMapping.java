package com.nexa.relay.domain.model;

import com.nexa.relay.domain.exception.InvalidRelayParameterException;

import java.util.Objects;

/**
 * 超管底仓映射聚合根（L2 层 A→B，全局，客户不可见，F-6011 / RL-7 第②步）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §4.1 + DB-SCHEMA §20。充血模型：唯一约束（一个公开名 A
 * 只能映射到一个 B，1对1）由仓储层唯一索引 {@code uk_public_name} 守护；启用/停用、改 B 等状态迁移在
 * 聚合方法上。</p>
 *
 * <p>可见性铁律（ADR-COMPAT-05）：{@code upstreamName}(B) 客户绝不可见——本聚合只通过 root/admin 路由读写，
 * 客户侧 API 绝不 join / 返回 B（三道闸之数据层）。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring），与 JPA 实体 {@code PlatformModelMappingJpaEntity} 分离，可纯单测。</p>
 */
public class PlatformModelMapping {

    /** 公开名/上游名最大长度，对齐 DB varchar(255)。 */
    public static final int NAME_MAX_LENGTH = 255;

    /** 自增主键，未持久化为 null。 */
    private Long id;

    /** A 平台公开名（唯一键）。 */
    private String publicName;

    /** B 真实上游模型名（客户绝不可见）。 */
    private String upstreamName;

    /** 是否启用（false=该映射停用，A 回落直通或 404）。 */
    private boolean enabled;

    /** 超管备注（可空）。 */
    private String remark;

    private final Long createdTime;
    private Long updatedTime;

    private PlatformModelMapping(Long id, String publicName, String upstreamName, boolean enabled,
                                 String remark, Long createdTime, Long updatedTime) {
        this.id = id;
        this.publicName = publicName;
        this.upstreamName = upstreamName;
        this.enabled = enabled;
        this.remark = remark;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
    }

    /**
     * 创建新映射（校验不变量：A/B 非空且 ≤255）。
     *
     * @param publicName   A 平台公开名
     * @param upstreamName B 真实上游名
     * @param remark       备注（可空）
     * @param nowEpoch     当前 epoch 秒
     * @return 新映射聚合（id 未赋，待仓储持久化）
     */
    public static PlatformModelMapping create(String publicName, String upstreamName, String remark, long nowEpoch) {
        validateName(publicName, "public_name");
        validateName(upstreamName, "upstream_name");
        return new PlatformModelMapping(null, publicName.trim(), upstreamName.trim(), true,
                remark, nowEpoch, nowEpoch);
    }

    /** 从持久化重建（仓储用，不重复校验存量）。 */
    public static PlatformModelMapping rehydrate(Long id, String publicName, String upstreamName,
                                                 boolean enabled, String remark, Long createdTime, Long updatedTime) {
        return new PlatformModelMapping(id, publicName, upstreamName, enabled, remark, createdTime, updatedTime);
    }

    /** 改 B（超管降本切换上游模型），校验非空。 */
    public void changeUpstream(String newUpstreamName, long nowEpoch) {
        validateName(newUpstreamName, "upstream_name");
        this.upstreamName = newUpstreamName.trim();
        this.updatedTime = nowEpoch;
    }

    /** 启用/停用。 */
    public void setEnabled(boolean enabled, long nowEpoch) {
        this.enabled = enabled;
        this.updatedTime = nowEpoch;
    }

    /** 改备注。 */
    public void changeRemark(String remark, long nowEpoch) {
        this.remark = remark;
        this.updatedTime = nowEpoch;
    }

    private static void validateName(String name, String field) {
        if (name == null || name.isBlank()) {
            throw new InvalidRelayParameterException(field + " must not be blank");
        }
        if (name.trim().length() > NAME_MAX_LENGTH) {
            throw new InvalidRelayParameterException(field + " exceeds max length " + NAME_MAX_LENGTH);
        }
    }

    public Long id() { return id; }
    public String publicName() { return publicName; }
    public String upstreamName() { return upstreamName; }
    public boolean isEnabled() { return enabled; }
    public String remark() { return remark; }
    public Long createdTime() { return createdTime; }
    public Long updatedTime() { return updatedTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlatformModelMapping that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(publicName, that.publicName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, publicName);
    }
}
