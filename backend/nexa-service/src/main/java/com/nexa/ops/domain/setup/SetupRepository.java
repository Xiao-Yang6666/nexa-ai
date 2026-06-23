package com.nexa.ops.domain.setup;

import java.util.Optional;

/**
 * 系统初始化标记仓储（领域接口，DDD 依赖倒置）。
 *
 * <p>承载 setups 表（单行哨兵）的领域级读写契约。用于 F-4015 状态探测（是否存在标记）、
 * F-4016 初始化提交（原子插入，幂等防并发双初始化）。</p>
 */
public interface SetupRepository {

    /**
     * 查询初始化标记（F-4015 状态探测）。
     *
     * @return 命中标记（存在即系统已初始化；空即未初始化）
     */
    Optional<SetupMarker> find();

    /**
     * 系统是否已初始化（= 存在初始化标记）。
     *
     * @return 已初始化返回 {@code true}
     */
    boolean isInitialized();

    /**
     * 原子地保存初始化标记（F-4016 幂等护栏）。
     *
     * <p>实现须保证幂等/并发安全：仅当标记不存在时插入成功，已存在时不覆盖并返回 {@code false}
     * （DB 主键唯一兜底并发双提交）。</p>
     *
     * @param marker 初始化标记
     * @return 本次新建成功返回 {@code true}；已存在（重复初始化）返回 {@code false}
     */
    boolean saveIfAbsent(SetupMarker marker);
}
