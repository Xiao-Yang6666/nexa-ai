package com.nexa.token.domain.repository;

import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 令牌仓储接口（领域层定义，基础设施层实现，F-3001~F-3012）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，可在单测中用 mock 替换。实现见
 * {@code infrastructure.persistence.TokenRepositoryImpl}。关联表：V9 {@code tokens}。</p>
 *
 * <p>self-scope 铁律：列表/搜索/批量操作一律带 {@code userId} 维度，仓储层 SQL 强制按 user_id 过滤，
 * 杜绝越权查到他人令牌（ROLE-PERMISSION-MATRIX §3）。</p>
 */
public interface TokenRepository {

    /**
     * 保存（新增或更新）令牌。
     *
     * <p>新建（id 为 null）保存后返回携带自增 id 的聚合。</p>
     *
     * @param token 待保存的令牌聚合
     * @return 持久化后的令牌（新建含 id）
     */
    Token save(Token token);

    /**
     * 按主键查令牌（不含已软删除）。
     *
     * @param id 令牌主键
     * @return 命中返回聚合，否则空
     */
    Optional<Token> findById(long id);

    /**
     * 按明文 key 查令牌（不含已软删除，F-3012 用量查询：tokenReadAuth 鉴权时按 key 反查归属）。
     *
     * <p>key 为 varchar(128) 唯一索引，等值查询。仅用于令牌鉴权路径或受控用量查询，绝不用于列表/搜索
     * （列表/搜索一律按 user_id 走 self-scope）。</p>
     *
     * @param key 完整明文 key
     * @return 命中返回聚合，否则空
     */
    Optional<Token> findByKey(String key);

    /**
     * 分页查询某用户的令牌（F-3002 列表，按 id 降序——新建在前）。
     *
     * @param userId     归属用户 id（self-scope，强制过滤）
     * @param pagination 分页参数
     * @return 当前页令牌列表
     */
    List<Token> findPageByUser(long userId, Pagination pagination);

    /**
     * 统计某用户的令牌总数（F-3002 列表 total）。
     *
     * @param userId 归属用户 id
     * @return 总条数
     */
    long countByUser(long userId);

    /**
     * 关键词/前缀搜索某用户的令牌（F-3003，按 name 模糊，按 id 降序）。
     *
     * @param userId     归属用户 id（self-scope，强制过滤）
     * @param keyword    关键词（空白→等价该用户全量分页）
     * @param pagination 分页参数
     * @return 当前页令牌列表
     */
    List<Token> searchByUser(long userId, String keyword, Pagination pagination);

    /**
     * 统计某用户关键词搜索命中总数（F-3003 total）。
     *
     * @param userId  归属用户 id
     * @param keyword 关键词（空白→等价该用户全量计数）
     * @return 命中总数
     */
    long countSearchByUser(long userId, String keyword);

    /**
     * 按一组 id 查某用户的令牌（F-3005 批量取明文 key / F-3007 批量删，强制 self-scope）。
     *
     * <p>仅返回归属 {@code userId} 的令牌——即便传入他人 id 也不会命中，从仓储层堵越权。</p>
     *
     * @param userId 归属用户 id
     * @param ids    令牌 id 列表
     * @return 命中且归属该用户的令牌列表（空列表表示无命中）
     */
    List<Token> findByUserAndIds(long userId, List<Long> ids);

    /**
     * 失效指定明文 key 的鉴权缓存（写穿，禁用/删除 token 后立即生效，T12/CR-05）。
     *
     * <p>由 token 管理用例在禁用/删除提交后调用，确保被禁/删 token 不再因缓存命中而通过 /v1/* 鉴权。
     * 缓存未启用或 Redis 不可用时为安全空操作（不抛出，不阻断管理流程）。</p>
     *
     * @param key 待失效的完整明文 key
     */
    void evictAuthCache(String key);

    /**
     * 软删除单个令牌（F-3007，写 deleted_at）。
     *
     * @param id 令牌主键
     */
    void softDeleteById(long id);

    /**
     * 批量软删除某用户的令牌（F-3007，强制 self-scope）。
     *
     * @param userId 归属用户 id（仅删本人令牌）
     * @param ids    令牌 id 列表
     * @return 实际删除条数
     */
    int softDeleteByUserAndIds(long userId, List<Long> ids);
}
