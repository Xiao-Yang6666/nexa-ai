package com.nexa.infrastructure.model.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.model.persistence.po.PublicModelPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 对外模型 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic} 驱动）。分页/排序/等值过滤由 {@link PublicModelRepositoryImpl} 用
 * {@code LambdaQueryWrapper} 组装；仅「投影出公开名列表」与「软删除写」用注解方法显式声明。</p>
 */
public interface PublicModelMapper extends BaseMapper<PublicModelPO> {

    /**
     * 上架公开名 A 列表（F-6003 候选层来源，F-6004 全员可用判定；仅投影 public_name，不含任何 B）。
     *
     * <p>等价原 JPQL {@code SELECT m.publicName ... WHERE enabled = true ORDER BY sort_order ASC, id ASC}。
     * 显式 {@code deleted_at IS NULL} 复现 {@code @SQLRestriction} 软删过滤（注解 SQL 不经 @TableLogic 自动追加）。</p>
     *
     * @return 上架对外模型公开名，按 sort_order/id 升序
     */
    @Select("SELECT public_name FROM public_models WHERE enabled = true AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC")
    List<String> findEnabledPublicNames();

    /**
     * 软删除：写 {@code deleted_at} epoch 秒时间戳（仅当未删，幂等）。
     *
     * <p>等价原 JPA {@code @Modifying UPDATE ... SET deleted_at WHERE id = :id AND deleted_at IS NULL}。
     * 删除值是 epoch 秒（非 0/1），故不依赖 {@code deleteById}，改显式 {@code @Update}。</p>
     *
     * @param id        目标对外模型 id
     * @param deletedAt 删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Update("UPDATE public_models SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
