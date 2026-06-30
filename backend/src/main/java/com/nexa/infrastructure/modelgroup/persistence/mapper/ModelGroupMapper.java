package com.nexa.infrastructure.modelgroup.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.modelgroup.persistence.po.ModelGroupPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 模型组 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic(value = "null")} 驱动）。派生查询（findByCode/findByAccessPolicy/findByIdIn/
 * existsByCode…）由 {@link ModelGroupRepositoryImpl} 用 {@code LambdaQueryWrapper} 组装。{@code models}
 * jsonb 列读写由 PO 上的 {@link com.nexa.infrastructure.persistence.JsonbStringTypeHandler} + autoResultMap 承载。</p>
 */
public interface ModelGroupMapper extends BaseMapper<ModelGroupPO> {

    /**
     * 软删除：写 {@code deleted_at} epoch 秒时间戳（仅当未删，幂等）。
     *
     * <p>等价原 JPA {@code findById + setDeletedAt + save}。因本项目删除值是 epoch 秒（非 0/1），不依赖
     * MyBatis-Plus {@code deleteById} 的默认逻辑删除值，改显式 {@code @Update}。{@code AND deleted_at IS NULL}
     * 保持命中已删行时不重复打时间戳的幂等语义，受影响行数 0 表示「不存在或已删」。</p>
     *
     * @param id        目标模型组 id
     * @param deletedAt 删除时间戳 epoch 秒
     * @return 受影响行数（0 = 不存在或已软删）
     */
    @Update("UPDATE model_groups SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
