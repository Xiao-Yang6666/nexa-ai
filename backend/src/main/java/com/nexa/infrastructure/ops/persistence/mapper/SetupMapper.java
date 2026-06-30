package com.nexa.infrastructure.ops.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.ops.persistence.po.SetupPO;

/**
 * 系统初始化标记 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectCount...）。仅供
 * {@link SetupRepositoryImpl} 内部使用。单行哨兵表，主键为 Integer（固定 1，{@code @TableId} INPUT 非自增）。
 * {@code saveIfAbsent} 的存在性短路 + 主键唯一兜底由 Impl 组装，不在此声明方法。</p>
 */
public interface SetupMapper extends BaseMapper<SetupPO> {
}
