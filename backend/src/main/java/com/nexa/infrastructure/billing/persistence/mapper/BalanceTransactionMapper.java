package com.nexa.infrastructure.billing.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.billing.persistence.po.BalanceTransactionPO;

/**
 * 账变流水 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectList/updateById/delete...）。
 * 仅供 {@link BalanceTransactionRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.BalanceTransactionRepository}。派生查询（如按用户倒序限量）由 Impl 内
 * {@code LambdaQueryWrapper} 组装，不在此声明方法。</p>
 */
public interface BalanceTransactionMapper extends BaseMapper<BalanceTransactionPO> {
}
