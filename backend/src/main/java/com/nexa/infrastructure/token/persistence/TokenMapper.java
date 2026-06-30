package com.nexa.infrastructure.token.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.token.persistence.po.TokenPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 令牌 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic} 驱动）。self-scope 查询、分页搜索由 {@link TokenRepositoryImpl} 用
 * {@code LambdaQueryWrapper} 组装。软删除写（单删 / self-scope 批量删）因删除值是 epoch 秒（非 0/1）
 * 不走 {@code deleteById}，改显式 {@code @Update}。</p>
 */
public interface TokenMapper extends BaseMapper<TokenPO> {

    /**
     * 软删除单个令牌（F-3007，写 deleted_at；仅当未删，幂等）。
     *
     * @param id        令牌主键
     * @param deletedAt 软删除时间戳 epoch 秒
     * @return 受影响行数（0=已删/不存在，1=本次删除）
     */
    @Update("UPDATE tokens SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);

    /**
     * 批量软删除某用户的令牌（F-3007，强制 self-scope）。
     *
     * @param userId    归属用户 id（INTEGER）
     * @param ids       id 集合（非空）
     * @param deletedAt 软删除时间戳 epoch 秒
     * @return 实际受影响行数
     */
    @Update("""
            <script>
            UPDATE tokens SET deleted_at = #{deletedAt}
            WHERE user_id = #{userId} AND deleted_at IS NULL AND id IN
            <foreach collection="ids" item="tid" open="(" separator="," close=")">#{tid}</foreach>
            </script>
            """)
    int softDeleteByUserAndIds(@Param("userId") int userId,
                               @Param("ids") List<Long> ids,
                               @Param("deletedAt") long deletedAt);
}
