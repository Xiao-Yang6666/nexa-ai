package com.nexa.infrastructure.modelgroup.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.modelgroup.persistence.po.ModelGroupAccessPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模型组访问授权 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（无软删除，物理删）。派生查询（findByModelGroupId/
 * existsBy…/deleteBy…）由 {@link ModelGroupAccessRepositoryImpl} 用 {@code LambdaQueryWrapper} 组装；
 * 「按主体查可访问组 id 列表」是列投影查询，单独以 {@link #findGroupIdsBySubject} {@code @Select} 声明。</p>
 */
public interface ModelGroupAccessMapper extends BaseMapper<ModelGroupAccessPO> {

    /**
     * 按主体查其被授权的模型组 id 列表（中继链路解析私有组可见性）。
     *
     * <p>等价原 JPQL {@code SELECT a.modelGroupId FROM ... WHERE subjectType AND subjectId}。
     * 列投影返回 {@code List<Long>}，故用 {@code @Select} 而非实体查询。</p>
     *
     * @param subjectType 主体类型字面量
     * @param subjectId   主体主键
     * @return 模型组 id 列表
     */
    @Select("SELECT model_group_id FROM model_group_access "
            + "WHERE subject_type = #{subjectType} AND subject_id = #{subjectId}")
    List<Long> findGroupIdsBySubject(@Param("subjectType") String subjectType,
                                     @Param("subjectId") Long subjectId);
}
