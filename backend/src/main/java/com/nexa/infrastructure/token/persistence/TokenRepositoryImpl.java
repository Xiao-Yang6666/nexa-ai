package com.nexa.infrastructure.token.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import com.nexa.domain.token.vo.Pagination;
import com.nexa.infrastructure.token.config.AuthCacheConfig;
import com.nexa.infrastructure.token.persistence.po.TokenPO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link TokenRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-3001~F-3012）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link TokenMapper} + PO 就近工厂映射实现它。
 * 领域聚合 {@link Token} 与 PO {@link TokenPO} 分离，domain 因此不感知持久化框架。
 * 软删除过滤由 PO 的 {@code @TableLogic} 自动在 select 追加 {@code deleted_at IS NULL}。</p>
 *
 * <p>self-scope 强制：列表/搜索/批量取明文/批量删 一律带 {@code userId} 维度，仓储层 SQL 显式过滤，
 * 即便上层漏判也无法越权查到他人令牌（ROLE-PERMISSION-MATRIX §3「self-scope 强制过滤」）。</p>
 *
 * <p>软删除：单删/批量删走 Mapper 显式 {@code @Update} 打 epoch 秒（不用 deleteById），写完后查询
 * 自动隐藏（{@code @TableLogic}）。</p>
 */
@Repository
public class TokenRepositoryImpl implements TokenRepository {

    private final TokenMapper mapper;

    /** @param mapper 令牌 MyBatis-Plus Mapper（infra 内部依赖） */
    public TokenRepositoryImpl(TokenMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Token save(Token token) {
        TokenPO entity = TokenPO.of(token);
        if (token.id() == null) {
            mapper.insert(entity);
            // 新建保存后回填自增主键到聚合（编辑路径已带 id 不需回填）。
            token.assignId(entity.getId());
        } else {
            mapper.updateById(entity);
        }
        return entity.toDomain();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Token> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(TokenPO::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>命脉热点：{@code /v1/*} 每次转发都按 key 反查 token 校验有效性/归属，挂 {@code @Cacheable}
     * 缓存反查结果（cache={@code apiKeyAuth}，key=apiKey 值，TTL 120s 见 {@link AuthCacheConfig}）。
     * 空白 key 不进缓存（{@code condition}）；未命中的空结果不缓存（{@code unless="#result == null"}——
     * Spring 对 {@code Optional} 返回值解包，空 {@code Optional} 的 {@code #result} 为 null）——避免刚建的
     * token 因负缓存在 120s 内鉴权失败。Redis 不可用时由 {@code CacheErrorHandler} 降级直查 DB。</p>
     */
    @Override
    @Cacheable(cacheNames = AuthCacheConfig.API_KEY_AUTH_CACHE, key = "#key",
            condition = "#key != null && !#key.isBlank()",
            unless = "#result == null")
    public Optional<Token> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<TokenPO>lambdaQuery()
                        .eq(TokenPO::getKey, key.trim())))
                .map(TokenPO::toDomain);
    }

    /**
     * 写穿失效：清掉指定 apiKey 的鉴权缓存（禁用/删除 token 后立即生效，不等 TTL）。
     *
     * <p>由 token 管理用例在禁用/删除后调用（彼时已加载 token，持有其明文 key）。eviction 由
     * {@code @CacheEvict} 注解驱动，本方法体为空——清除动作由缓存切面完成。Redis 不可用时由
     * {@code CacheErrorHandler} 吞掉异常，不阻断管理操作。</p>
     *
     * @param key 待失效的完整明文 key（与缓存写入键一致）
     */
    @Override
    @CacheEvict(cacheNames = AuthCacheConfig.API_KEY_AUTH_CACHE, key = "#key",
            condition = "#key != null && !#key.isBlank()")
    public void evictAuthCache(String key) {
        // no-op：失效由 @CacheEvict 切面执行。
    }

    /** {@inheritDoc} */
    @Override
    public List<Token> findPageByUser(long userId, Pagination pagination) {
        return mapper.selectList(pageByUserWrapper(userId, null, pagination))
                .stream().map(TokenPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countByUser(long userId) {
        return mapper.selectCount(Wrappers.<TokenPO>lambdaQuery()
                .eq(TokenPO::getUserId, toIntUserId(userId)));
    }

    /** {@inheritDoc} */
    @Override
    public List<Token> searchByUser(long userId, String keyword, Pagination pagination) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        // 空关键词等价该用户全量分页（复用列表查询，避免在搜索 SQL 里堆 OR/IS NULL）。
        return mapper.selectList(pageByUserWrapper(userId, kw.isEmpty() ? null : kw, pagination))
                .stream().map(TokenPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countSearchByUser(long userId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        LambdaQueryWrapper<TokenPO> w = Wrappers.<TokenPO>lambdaQuery()
                .eq(TokenPO::getUserId, toIntUserId(userId));
        if (!kw.isEmpty()) {
            // 大小写不敏感模糊（name COALESCE 空串），{0} 占位防注入。
            w.apply("LOWER(COALESCE(name, '')) LIKE LOWER({0})", "%" + kw + "%");
        }
        return mapper.selectCount(w);
    }

    /** {@inheritDoc} */
    @Override
    public List<Token> findByUserAndIds(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<TokenPO>lambdaQuery()
                        .eq(TokenPO::getUserId, toIntUserId(userId))
                        .in(TokenPO::getId, ids)).stream()
                .map(TokenPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void softDeleteById(long id) {
        mapper.softDeleteById(id, Instant.now().getEpochSecond());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public int softDeleteByUserAndIds(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return mapper.softDeleteByUserAndIds(toIntUserId(userId), ids, Instant.now().getEpochSecond());
    }

    /**
     * 组装某用户令牌的分页查询 wrapper：self-scope（user_id）+ 可选关键词模糊 + id 降序 + LIMIT/OFFSET。
     *
     * @param userId     归属用户 id
     * @param lowerKw    已归一小写的关键词（null=不过滤）
     * @param pagination 分页（1-based）
     * @return 组装好的 wrapper
     */
    private LambdaQueryWrapper<TokenPO> pageByUserWrapper(long userId, String lowerKw, Pagination pagination) {
        int page = Math.max(1, pagination.page());
        int size = Math.max(1, pagination.pageSize());
        int offset = (page - 1) * size;
        LambdaQueryWrapper<TokenPO> w = Wrappers.<TokenPO>lambdaQuery()
                .eq(TokenPO::getUserId, toIntUserId(userId));
        if (lowerKw != null && !lowerKw.isEmpty()) {
            w.apply("LOWER(COALESCE(name, '')) LIKE LOWER({0})", "%" + lowerKw + "%");
        }
        // 按 id 降序（新建在前），LIMIT/OFFSET 取当前页；page/size 已 clamp 为可信整数。
        w.orderByDesc(TokenPO::getId).last("LIMIT " + size + " OFFSET " + offset);
        return w;
    }

    /**
     * 将领域 long 用户 id 安全转为 DB INTEGER（越界即抛，不静默截断）。
     */
    private int toIntUserId(long userId) {
        if (userId > Integer.MAX_VALUE || userId < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("userId out of INTEGER range: " + userId);
        }
        return (int) userId;
    }
}
