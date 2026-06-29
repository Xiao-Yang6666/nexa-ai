package com.nexa.domain.modelgroup.vo;

/**
 * 模型组状态值对象（启用/禁用二态）。
 *
 * <p>禁用的模型组对中继链路不可选（即便有访问授权也不放行），但保留配置与历史授权（区别于软删除）。
 * 落库为整数码（1=启用 2=禁用），与现网渠道/令牌状态整数语义兼容；脏码归并为禁用（安全默认）。</p>
 */
public enum ModelGroupStatus {

    /** 启用（可被中继链路选用）。 */
    ENABLED(1),

    /** 禁用（保留配置但不可选用）。 */
    DISABLED(2);

    private final int code;

    ModelGroupStatus(int code) {
        this.code = code;
    }

    /** @return 落库整数码（1/2） */
    public int code() {
        return code;
    }

    /** @return 是否启用 */
    public boolean isEnabled() {
        return this == ENABLED;
    }

    /**
     * 由整数码解析状态（持久化重建 / 状态切换入参）。
     *
     * <p>仅 1 归并为 ENABLED，其余一律 DISABLED（脏码安全默认为禁用，绝不把未知态当启用放行）。</p>
     *
     * @param code 整数码
     * @return 对应状态
     */
    public static ModelGroupStatus fromCode(int code) {
        return code == ENABLED.code ? ENABLED : DISABLED;
    }
}
