package com.nexa.infrastructure.relay.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.relay.persistence.po.UserModelAliasPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 客户层别名 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD；PO 上 {@code @TableLogic(value="null")} 使
 * select 自动追加 {@code deleted_at IS NULL}（等价迁移前各条 JPQL 的显式过滤）。复合查询
 * （按 scope+alias、按 id、按 scope 列表）由 {@link UserModelAliasRepositoryImpl} 内
 * {@code LambdaQueryWrapper} 组装。软删除写不依赖 {@code deleteById}（{@code @TableLogic} 未声明
 * delval，与本项目 epoch 秒不符），改由下方显式 {@code @Update} 落 {@code deleted_at}。</p>
 */
public interface UserModelAliasMapper extends BaseMapper<UserModelAliasPO> {

    /**
     * 软删除：把活跃行的 {@code deleted_at} 置为给定 epoch 秒（仅影响当前未删行，与原
     * {@code findActiveById + setDeletedAt + save} 行为 1:1）。
     *
     * @param id        别名主键
     * @param deletedAt 删除时间戳（epoch 秒）
     * @return 受影响行数（0 表示该 id 不存在或已删）
     */
    @Update("UPDATE user_model_aliases SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
