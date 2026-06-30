package com.nexa.infrastructure.account.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.account.persistence.po.UserPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic} 驱动）。派生查询（findByUsername/existsByUsername/分页搜索）由
 * {@link UserRepositoryImpl} 用 {@code LambdaQueryWrapper} 组装。</p>
 */
public interface UserMapper extends BaseMapper<UserPO> {

    /**
     * 软删除：写 {@code deleted_at} epoch 秒时间戳（仅当未删，幂等）。
     *
     * <p>等价原 JPA {@code @Modifying UPDATE ... SET deleted_at WHERE id = :id}。因本项目删除值是
     * epoch 秒（非 0/1），不依赖 MyBatis-Plus {@code deleteById} 的默认逻辑删除值，改显式 {@code @Update}。
     * {@code AND deleted_at IS NULL} 保持命中已删行时不重复打时间戳的幂等语义。</p>
     *
     * @param id        目标用户 id
     * @param deletedAt 删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Update("UPDATE users SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int markDeleted(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
