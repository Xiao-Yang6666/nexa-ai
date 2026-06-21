package com.nexa.model.domain.repository;

import com.nexa.model.domain.model.UserModelAlias;
import com.nexa.model.domain.vo.AliasScopeType;

import java.util.List;
import java.util.Optional;

/**
 * 客户层自助映射仓储接口（C→A，领域层定义，基础设施层实现，F-6003）。
 *
 * <p>DDD 依赖倒置（backend-engineer §2.3）。关联表：V13 {@code user_model_aliases}。
 * 越权护栏（self-scope）由应用层落地，本仓储仅按 (scope_type, scope_id) 过滤查询。</p>
 */
public interface UserModelAliasRepository {

    /**
     * 保存（新增或更新）映射。新建保存后返回携带自增 id 的聚合。
     *
     * @param alias 待保存的映射聚合
     * @return 持久化后的映射（新建含 id）
     */
    UserModelAlias save(UserModelAlias alias);

    /**
     * 按主键查映射。
     *
     * @param id 主键
     * @return 命中返回聚合，否则空
     */
    Optional<UserModelAlias> findById(long id);

    /**
     * 按 (scope_type, scope_id, alias) 查映射（幂等键查重，F-6003 同作用域 C 唯一）。
     *
     * @param scopeType 作用域类型
     * @param scopeId   作用域 id
     * @param aliasName 客户别名 C
     * @return 命中返回聚合，否则空
     */
    Optional<UserModelAlias> findByScopeAndAlias(AliasScopeType scopeType, String scopeId, String aliasName);

    /**
     * 列出某 (scope_type, scope_id) 下的全部映射（F-6003 列表，按 alias 升序）。
     *
     * @param scopeType 作用域类型
     * @param scopeId   作用域 id
     * @return 该作用域下全部映射
     */
    List<UserModelAlias> findByScope(AliasScopeType scopeType, String scopeId);

    /**
     * 列出某用户可见的全部映射：本人 user-scope + 所属各 group-scope 合并（F-6003 列表）。
     *
     * <p>结果按「优先级 user&gt;group」要求由调用方组合，不在本仓储排序合成（仓储只查数据）。</p>
     *
     * @param userId       当前用户 id（user-scope 过滤键）
     * @param userGroups   该用户所属分组名集合（group-scope 过滤键，可空集 → 仅 user-scope）
     * @return 本人 + 组内映射合并列表
     */
    List<UserModelAlias> findByUserAndGroups(long userId, List<String> userGroups);

    /**
     * 软删除映射（F-6003）。
     *
     * @param id 主键
     */
    void deleteById(long id);
}
