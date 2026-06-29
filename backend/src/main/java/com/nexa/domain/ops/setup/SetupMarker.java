package com.nexa.domain.ops.setup;

import java.time.Instant;

/**
 * 系统初始化标记（聚合根，对齐表 setups / F-4016 Create(model.Setup)）。
 *
 * <p>「系统已完成首次部署引导」的单行哨兵记录。一旦持久化即代表已初始化，是 POST /api/setup
 * 重复提交的幂等护栏（已存在 → 拒绝）与 GET /api/setup 状态探测的依据。</p>
 *
 * <p>固定主键 {@link #SINGLETON_ID}（恒为 1）表达「全站单行」语义，DB 层 PK 唯一兜底并发双提交。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §9.1 POST /api/setup 副作用「Create(model.Setup)；constant.Setup=true」。</p>
 */
public final class SetupMarker {

    /** 单行哨兵固定主键（全站只允许一条初始化标记）。 */
    public static final int SINGLETON_ID = 1;

    private final int id;
    private final String version;
    private final long initializedAt;

    private SetupMarker(int id, String version, long initializedAt) {
        this.id = id;
        this.version = version;
        this.initializedAt = initializedAt;
    }

    /**
     * 新建初始化标记（初始化提交成功时调用，记当前时间）。
     *
     * @param version 引导版本标记
     * @return 新标记（id = SINGLETON_ID）
     */
    public static SetupMarker create(String version) {
        return new SetupMarker(SINGLETON_ID, version, Instant.now().getEpochSecond());
    }

    /**
     * 从持久化数据重建（基础设施层映射用）。
     *
     * @param id            主键
     * @param version       引导版本
     * @param initializedAt 初始化时间 epoch 秒
     * @return 重建的标记
     */
    public static SetupMarker rehydrate(int id, String version, long initializedAt) {
        return new SetupMarker(id, version, initializedAt);
    }

    /** @return 主键 */
    public int id() {
        return id;
    }

    /** @return 引导版本标记 */
    public String version() {
        return version;
    }

    /** @return 初始化完成时间 epoch 秒 */
    public long initializedAt() {
        return initializedAt;
    }
}
