package com.nexa.infrastructure.model.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.model.persistence.po.UserModelAliasPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 客户层自助映射 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic} 驱动）。按 scope 的等值/合并/排序查询由 {@link UserModelAliasRepositoryImpl} 用
 * {@code LambdaQueryWrapper} 组装；仅软删除写用注解方法显式声明。</p>
 */
public interface UserModelAliasMapper extends BaseMapper<UserModelAliasPO> {

    /**
     * 软删除：写 {@code deleted_at} epoch 秒时间戳（仅当未删，幂等）。
     *
     * <p>等价原 JPA {@code @Modifying UPDATE ... SET deleted_at WHERE id = :id AND deleted_at IS NULL}。
     * 删除值是 epoch 秒（非 0/1），故不依赖 {@code deleteById}，改显式 {@code @Update}。</p>
     *
     * @param id        目标映射 id
     * @param deletedAt 删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Update("UPDATE user_model_aliases SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
