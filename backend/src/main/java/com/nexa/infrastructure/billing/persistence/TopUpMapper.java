package com.nexa.infrastructure.billing.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.billing.persistence.po.TopUpPO;

/**
 * 充值订单 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectOne/updateById/delete...）。
 * 仅供 {@link TopUpRepositoryImpl} 内部使用，领域只认 {@code domain.repository.TopUpRepository}。
 * 按商户订单号定位（{@code findByTradeNo}）由 Impl 内 {@code LambdaQueryWrapper} 组装，不在此声明方法。</p>
 */
public interface TopUpMapper extends BaseMapper<TopUpPO> {
}
