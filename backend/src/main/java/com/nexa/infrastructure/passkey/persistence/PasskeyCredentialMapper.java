package com.nexa.infrastructure.passkey.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexa.infrastructure.passkey.persistence.po.PasskeyCredentialPO;

/**
 * Passkey 凭据 MyBatis-Plus Mapper（基础设施层内部接口）。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD（insert/selectById/selectOne/selectList/updateById/delete...）。
 * 仅供 {@link PasskeyCredentialRepositoryImpl} 内部使用，领域只认
 * {@code domain.repository.PasskeyCredentialRepository}。按 user_id / credential_id 唯一索引的派生查询，
 * 以及按 user_id 物理删除，由 Impl 内 {@code LambdaQueryWrapper} 组装，不在此声明方法。</p>
 */
public interface PasskeyCredentialMapper extends BaseMapper<PasskeyCredentialPO> {
}
