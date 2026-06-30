package com.nexa.infrastructure.telegram.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.telegram.persistence.po.TelegramBindingPO;

/**
 * Telegram 绑定 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectOne/selectList/updateById/delete...）。
 * 仅供 {@link TelegramBindingRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.TelegramBindingRepository}。按 telegram_id / user_id 唯一索引的派生查询由 Impl 内
 * {@code LambdaQueryWrapper} 组装，不在此声明方法。</p>
 */
public interface TelegramBindingMapper extends BaseMapper<TelegramBindingPO> {
}
