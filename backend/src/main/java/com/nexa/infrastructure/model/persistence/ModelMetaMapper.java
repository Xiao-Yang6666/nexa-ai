package com.nexa.infrastructure.model.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.model.persistence.po.ModelMetaPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 模型元数据 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic} 驱动）。findByModelName/分页/多条件搜索由 {@link ModelMetaRepositoryImpl} 用
 * {@code LambdaQueryWrapper} 组装；按供应商分组计数与软删写为聚合/写 SQL，用注解方法声明在此。仅供
 * {@link ModelMetaRepositoryImpl} 内部使用，领域只认 {@code domain.repository.ModelMetaRepository}。</p>
 */
public interface ModelMetaMapper extends BaseMapper<ModelMetaPO> {

    /**
     * 按供应商分组计数（F-3013 vendor_counts，一次聚合避免 N+1）。
     *
     * <p>等价原 JPQL {@code SELECT m.vendorId, COUNT(m) ... GROUP BY m.vendorId}。手写 SQL 显式补
     * {@code WHERE deleted_at IS NULL}（注解 SQL 不经 {@code @TableLogic} 自动追加）。每行返回
     * {@code {vendor_id=<Long 可空>, cnt=<Long>}}，由 {@link ModelMetaRepositoryImpl#countByVendor()} 适配。</p>
     *
     * @return 每个供应商一行的计数映射（vendor_id 可能为 null）
     */
    @Select("SELECT vendor_id, COUNT(*) AS cnt FROM model_metas WHERE deleted_at IS NULL GROUP BY vendor_id")
    List<Map<String, Object>> countGroupByVendor();

    /**
     * 软删除模型：写 {@code deleted_at} epoch 秒时间戳（仅当未删，幂等）。
     *
     * <p>等价原 JPA {@code @Modifying UPDATE ... SET deleted_at WHERE id = :id AND deleted_at IS NULL}。
     * 因本项目删除值是 epoch 秒（非 0/1），不依赖 MyBatis-Plus {@code deleteById} 的默认逻辑删除值，改显式
     * {@code @Update}。</p>
     *
     * @param id        目标模型 id
     * @param deletedAt 删除时间戳 epoch 秒
     * @return 受影响行数
     */
    @Update("UPDATE model_metas SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
    int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
}
