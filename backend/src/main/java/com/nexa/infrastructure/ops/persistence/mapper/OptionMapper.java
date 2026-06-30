package com.nexa.infrastructure.ops.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.ops.persistence.po.OptionPO;

/**
 * 全站选项 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectList/updateById/deleteById...）。
 * 仅供 {@link OptionRepositoryImpl} 内部使用，领域只认 {@code domain.ops.option.OptionRepository}。
 * 主键为 String（{@code key}，PG 保留字，{@code @TableId} 已双引号转义）。覆盖式写入的 merge 语义
 * （存在更新、不存在插入，对齐 F-4018 单键幂等覆盖）由 Impl 用 selectById 判断分支组装，不在此声明方法。</p>
 */
public interface OptionMapper extends BaseMapper<OptionPO> {
}
