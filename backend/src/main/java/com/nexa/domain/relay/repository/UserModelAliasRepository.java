package com.nexa.domain.relay.repository;

import com.nexa.domain.relay.model.UserModelAlias;
import com.nexa.domain.relay.vo.AliasScope;

import java.util.List;
import java.util.Optional;

/**
 * 客户层别名仓储接口（domain 定接口，infrastructure 实现，backend-engineer §2.3）。
 *
 * <p>RL-7 第②步 L1 解析 + 用户自助 CRUD。</p>
 */
public interface UserModelAliasRepository {

    /**
     * 按作用域 + 别名 C 查目标 A（L1 链式解析单步）。
     *
     * <p>优先级：user > group（调用方先传 user scope，未命中再传 group scope）。</p>
     */
    Optional<String> findTargetByAlias(AliasScope scope, String alias);

    Optional<UserModelAlias> findById(Long id);

    /** 按作用域列出全部别名（用户自助列表）。 */
    List<UserModelAlias> findByScope(AliasScope scope);

    void save(UserModelAlias alias);
    void deleteById(Long id);
}
