package com.nexa.model.domain.model;

import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.vo.ModelStatus;

import java.time.Instant;

/**
 * 供应商元数据聚合根（充血领域模型，F-3018）。
 *
 * <p>领域规则来源：DB-SCHEMA 模块四 §4.2 + PRD ML-1。供应商是模型的归属维度（model_metas.vendor_id
 * → vendor_metas.id），承载名称（业务唯一幂等键）、图标、状态。本聚合守护：
 * <ul>
 *   <li>name 必填非空白（创建/更新均校验）——空 → 「供应商名称不能为空」（F-3018）。</li>
 *   <li>name 唯一由应用层 + DB 唯一索引 uk_vendor_name_alive 双重保证（聚合内不持有全局视图，
 *       唯一性是跨聚合不变量，落应用层校验，backend-engineer §2.2）。</li>
 *   <li>状态用值对象 {@link ModelStatus}，缺省启用。</li>
 * </ul>
 * </p>
 *
 * <p>DDD：domain 零框架依赖（纯 Java，可单测无需起 Spring/DB）。行为在聚合方法上（create/rename/
 * updateMeta），非贫血 getter/setter。</p>
 */
public class Vendor {

    /** 供应商名最大长度（对齐 V10 {@code name varchar(255)}）。 */
    public static final int NAME_MAX_LENGTH = 255;

    private Long id;
    private String name;
    private String icon;
    private ModelStatus status;
    private Long createdTime;
    private Long updatedTime;

    private Vendor() {
    }

    /**
     * 创建新供应商（F-3018 POST，未持久化，id 为 null）。
     *
     * @param name   供应商名（必填非空白，≤255）
     * @param icon   图标（可空）
     * @param status 状态码（可空 → 缺省启用）
     * @return 新建供应商聚合
     * @throws InvalidModelParameterException name 为空白或超长
     */
    public static Vendor create(String name, String icon, Integer status) {
        Vendor v = new Vendor();
        v.name = normalizeName(name);
        v.icon = blankToNull(icon);
        v.status = status == null ? ModelStatus.ENABLED : ModelStatus.fromCode(status);
        long now = Instant.now().getEpochSecond();
        v.createdTime = now;
        v.updatedTime = now;
        return v;
    }

    /**
     * 从持久化重建聚合（基础设施层用，绕过创建校验，信任已落库数据）。
     *
     * @param id           主键
     * @param name         供应商名
     * @param icon         图标
     * @param status       状态码
     * @param createdTime  创建时间 epoch 秒
     * @param updatedTime  更新时间 epoch 秒
     * @return 重建的聚合
     */
    public static Vendor rehydrate(Long id, String name, String icon, int status,
                                   Long createdTime, Long updatedTime) {
        Vendor v = new Vendor();
        v.id = id;
        v.name = name;
        v.icon = icon;
        v.status = ModelStatus.fromCode(status);
        v.createdTime = createdTime;
        v.updatedTime = updatedTime;
        return v;
    }

    /**
     * 覆盖式更新供应商元数据（F-3018 PUT）。
     *
     * @param name   新名称（必填非空白，≤255）
     * @param icon   新图标（可空 → 清空）
     * @param status 新状态码（可空 → 不改）
     * @throws InvalidModelParameterException name 非法
     */
    public void updateMeta(String name, String icon, Integer status) {
        this.name = normalizeName(name);
        this.icon = blankToNull(icon);
        if (status != null) {
            this.status = ModelStatus.fromCode(status);
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

    /**
     * 供应商名归一与校验（去空白、非空、长度护栏）。
     *
     * @param raw 原始名
     * @return 归一后名称
     * @throws InvalidModelParameterException 空白或超长
     */
    private static String normalizeName(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            // 领域规则来源：F-3018 BACKLOG T-120「Name 空返回供应商名称不能为空」。
            throw new InvalidModelParameterException("供应商名称不能为空");
        }
        if (n.length() > NAME_MAX_LENGTH) {
            throw new InvalidModelParameterException("供应商名称长度不能超过 " + NAME_MAX_LENGTH);
        }
        return n;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ---- 访问器（读侧，无 setter；状态变更经聚合方法） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 供应商名 */
    public String name() {
        return name;
    }

    /** @return 图标（可空） */
    public String icon() {
        return icon;
    }

    /** @return 状态值对象 */
    public ModelStatus status() {
        return status;
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
