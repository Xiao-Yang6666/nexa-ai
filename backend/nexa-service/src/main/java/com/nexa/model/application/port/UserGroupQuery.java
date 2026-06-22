package com.nexa.model.application.port;

import java.util.Optional;

/**
 * 用户分组查询端口（应用层 / 防腐层接口，F-3025）。
 *
 * <p>用户可见模型列表需按「用户所属分组」聚合，但 User 是 com.nexa.account 的聚合根，模型上下文
 * 不直接依赖。故定义最小只读端口：按 user_id 取分组标识，基础设施层用 account 仓储实现。
 * 用户不存在时返回 empty（用例据此返回「用户不存在」，BACKLOG T-127）。</p>
 */
public interface UserGroupQuery {

    /**
     * 取用户所属分组。
     *
     * @param userId 用户 id
     * @return 命中返回分组标识，用户不存在返回 empty
     */
    Optional<String> groupOf(long userId);
}
