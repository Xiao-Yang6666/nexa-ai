package com.nexa.model.domain.model;

import com.nexa.model.domain.exception.InvalidModelParameterException;

import java.time.Instant;

/**
 * 超管底仓映射聚合根（A→B，全局，充血领域模型，F-6002）。
 *
 * <p>领域规则来源：COMPAT-BILLING-DECISIONS §2「两层模型映射」L2 层（A→B）+ DB-SCHEMA §17
 * PlatformModelMapping。生效顺序 L2（在选渠之前、L1 客户层 C→A 之后），作用域全局无 group/user 维。
 * 1对1 纯字符串替换：一个公开名 A 唯一对应一个真实上游模型 B。</p>
 *
 * <p><b>B 不可见三道闸</b>（COMPAT §2，本聚合是数据层闸的承载）：{@code upstreamName}（B）<b>客户绝不可见</b>，
 * 无任何 user 路由读 B 接口；本聚合的视图裁剪在接口层只对 admin/root 暴露 B。</p>
 *
 * <p>本聚合守护的不变量：
 * <ul>
 *   <li>{@code publicName}（A）必填非空白、≤255。</li>
 *   <li>{@code upstreamName}（B）必填非空白、≤255（not null，DB-SCHEMA §17）。</li>
 *   <li>{@code publicName} 全局唯一（uk_public_name，保证 1对1）由应用层 + DB 唯一索引双重保证。</li>
 * </ul>
 * </p>
 *
 * <p>DDD：domain 零框架依赖。行为在聚合方法上（create/update），非贫血。</p>
 */
public class PlatformModelMapping {

    /** A/B 名最大长度（对齐 DB-SCHEMA §17 varchar(255)）。 */
    public static final int NAME_MAX_LENGTH = 255;

    private Long id;
    private String publicName;
    private String upstreamName;
    private Boolean enabled;
    private String remark;
    private Long createdTime;
    private Long updatedTime;

    private PlatformModelMapping() {
    }

    /**
     * 创建新 A→B 底仓映射（F-6002 POST，未持久化，id 为 null）。
     *
     * @param publicName   对外名 A（必填非空白，≤255，幂等键）
     * @param upstreamName 真实上游名 B（必填非空白，≤255，客户绝不可见）
     * @param enabled      是否启用（可空 → true；false=回落直通或 404）
     * @param remark       备注（可空）
     * @return 新建映射聚合
     * @throws InvalidModelParameterException A 或 B 为空白/超长
     */
    public static PlatformModelMapping create(String publicName, String upstreamName,
                                              Boolean enabled, String remark) {
        PlatformModelMapping m = new PlatformModelMapping();
        m.publicName = normalizeName(publicName, "对外名");
        m.upstreamName = normalizeName(upstreamName, "上游模型名");
        m.enabled = enabled == null || enabled;
        m.remark = blankToEmpty(remark);
        long now = Instant.now().getEpochSecond();
        m.createdTime = now;
        m.updatedTime = now;
        return m;
    }

    /**
     * 从持久化重建聚合（基础设施层用，绕过创建校验）。
     *
     * @param id           主键
     * @param publicName   对外名 A
     * @param upstreamName 真实上游名 B
     * @param enabled      是否启用
     * @param remark       备注
     * @param createdTime  创建时间 epoch 秒
     * @param updatedTime  更新时间 epoch 秒
     * @return 重建的聚合
     */
    public static PlatformModelMapping rehydrate(Long id, String publicName, String upstreamName,
                                                 Boolean enabled, String remark,
                                                 Long createdTime, Long updatedTime) {
        PlatformModelMapping m = new PlatformModelMapping();
        m.id = id;
        m.publicName = publicName;
        m.upstreamName = upstreamName;
        m.enabled = enabled == null || enabled;
        m.remark = remark == null ? "" : remark;
        m.createdTime = createdTime;
        m.updatedTime = updatedTime;
        return m;
    }

    /**
     * 覆盖式更新映射（F-6002 PUT）。
     *
     * <p>A（public_name）是 1对1 幂等键，更新不可改 A（对齐 openapi PlatformModelMappingUpdateRequest
     * 无 public_name）；仅可改 B/启用/备注。</p>
     *
     * @param upstreamName 新上游名 B（可空 → 不改；非空白则校验长度）
     * @param enabled      新启用态（可空 → 不改）
     * @param remark       新备注（可空 → 不改）
     * @throws InvalidModelParameterException B 非法
     */
    public void update(String upstreamName, Boolean enabled, String remark) {
        if (upstreamName != null && !upstreamName.isBlank()) {
            this.upstreamName = normalizeName(upstreamName, "上游模型名");
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (remark != null) {
            this.remark = remark.trim();
        }
        touch();
    }

    /** 持久化后回填自增 id（仅基础设施层 save 后调用）。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private void touch() {
        this.updatedTime = Instant.now().getEpochSecond();
    }

    private static String normalizeName(String raw, String label) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            throw new InvalidModelParameterException(label + "不能为空");
        }
        if (n.length() > NAME_MAX_LENGTH) {
            throw new InvalidModelParameterException(label + "长度不能超过 " + NAME_MAX_LENGTH);
        }
        return n;
    }

    private static String blankToEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }

    // ---- 访问器（读侧，无 setter） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 对外名 A */
    public String publicName() {
        return publicName;
    }

    /** @return 真实上游名 B（客户绝不可见，仅 admin/root 视图暴露） */
    public String upstreamName() {
        return upstreamName;
    }

    /** @return 是否启用 */
    public Boolean enabled() {
        return enabled;
    }

    /** @return 备注 */
    public String remark() {
        return remark;
    }

    /** @return 创建时间 epoch 秒 */
    public Long createdTime() {
        return createdTime;
    }

    /** @return 更新时间 epoch 秒 */
    public Long updatedTime() {
        return updatedTime;
    }
}
