package com.nexa.infrastructure.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.billing.persistence.po.RedemptionPO;

/**
 * 兑换码 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（select 自动追加 {@code deleted_at IS NULL}，由 PO 的
 * {@code @TableLogic} 驱动）。仅供 {@link RedemptionRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.RedemptionRepository}。派生查询（按 key 查、按 id 降序分页）由 Impl 内
 * {@code LambdaQueryWrapper} 组装，本聚合无软删除写路径，故不声明额外方法。</p>
 */
public interface RedemptionMapper extends BaseMapper<RedemptionPO> {
}
