package com.nexa.token.infrastructure.persistence;

import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.repository.TokenRepository;
import com.nexa.token.domain.vo.Pagination;
import com.nexa.token.infrastructure.persistence.entity.TokenJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link TokenRepository} 的 JPA 实现（基础设施层适配器，F-3001~F-3012）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataTokenJpaRepository}
 * + 实体↔领域映射实现它。领域聚合 {@link Token} 与 JPA 实体 {@link TokenJpaEntity} 分离，
 * 映射集中在此处，domain 因此不感知 Hibernate（backend-engineer §2.3）。</p>
 *
 * <p>self-scope 强制：列表/搜索/批量取明文/批量删 一律带 {@code userId} 维度，仓储层 SQL 显式过滤，
 * 即便上层漏判也无法越权查到他人令牌（ROLE-PERMISSION-MATRIX §3「self-scope 强制过滤」）。</p>
 *
 * <p>软删除：单删/批量删走 {@code @Modifying UPDATE deleted_at}（绕过 {@code @SQLRestriction}），
 * 写完后查询自动隐藏（用例不需要再写 deleted_at IS NULL）。</p>
 */
@Repository
public class TokenRepositoryImpl implements TokenRepository {

    private final SpringDataTokenJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public TokenRepositoryImpl(SpringDataTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Token save(Token token) {
        TokenJpaEntity entity = toEntity(token);
        TokenJpaEntity saved = jpa.save(entity);
        if (token.id() == null) {
            // 新建保存后回填自增主键到聚合（编辑路径已带 id 不需回填）。
            token.assignId(saved.getId());
        }
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Token> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Token> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return jpa.findByKey(key.trim()).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<Token> findPageByUser(long userId, Pagination pagination) {
        Pageable pageable = PageRequest.of(pagination.page() - 1, pagination.pageSize());
        // userId 在领域为 long（兼容 BIGINT 客户端解析），DB 列为 INTEGER（DB-SCHEMA §2），强转 int。
        return jpa.findPageByUser(toIntUserId(userId), pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countByUser(long userId) {
        return jpa.countByUser(toIntUserId(userId));
    }

    /** {@inheritDoc} */
    @Override
    public List<Token> searchByUser(long userId, String keyword, Pagination pagination) {
        Pageable pageable = PageRequest.of(pagination.page() - 1, pagination.pageSize());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            // 空关键词等价该用户全量分页（复用列表查询，避免在搜索 SQL 里堆 OR/IS NULL）。
            return jpa.findPageByUser(toIntUserId(userId), pageable).stream().map(this::toDomain).toList();
        }
        return jpa.searchByUser(toIntUserId(userId), kw, pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countSearchByUser(long userId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            return jpa.countByUser(toIntUserId(userId));
        }
        return jpa.countSearchByUser(toIntUserId(userId), kw);
    }

    /** {@inheritDoc} */
    @Override
    public List<Token> findByUserAndIds(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByUserAndIds(toIntUserId(userId), ids).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void softDeleteById(long id) {
        jpa.softDeleteById(id, Instant.now().getEpochSecond());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public int softDeleteByUserAndIds(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jpa.softDeleteByUserAndIds(toIntUserId(userId), ids, Instant.now().getEpochSecond());
    }

    // ---- 实体 ↔ 领域映射 ----

    private TokenJpaEntity toEntity(Token token) {
        TokenJpaEntity e = new TokenJpaEntity();
        e.setId(token.id());
        e.setUserId(toIntUserId(token.userId()));
        e.setKey(token.key());
        e.setStatus(token.status().code());
        e.setName(token.name());
        e.setCreatedTime(token.createdTime());
        e.setAccessedTime(token.accessedTime());
        e.setExpiredTime(token.expiredTime());
        e.setRemainQuota(token.remainQuota());
        e.setUnlimitedQuota(token.unlimitedQuota());
        e.setModelLimitsEnabled(token.modelLimitsEnabled());
        e.setModelLimits(emptyToNull(token.modelLimits()));
        e.setAllowIps(token.allowIps() == null ? "" : token.allowIps());
        e.setUsedQuota(token.usedQuota());
        e.setGroup(token.group() == null ? "" : token.group());
        e.setCrossGroupRetry(token.crossGroupRetry());
        e.setEndpointLimitsEnabled(token.endpointLimitsEnabled());
        e.setEndpointLimits(emptyToNull(token.endpointLimits()));
        return e;
    }

    private Token toDomain(TokenJpaEntity e) {
        return Token.rehydrate(
                e.getId(),
                e.getUserId() == null ? 0L : e.getUserId().longValue(),
                e.getKey(),
                e.getStatus(),
                e.getName(),
                e.getExpiredTime(),
                e.getRemainQuota(),
                e.isUnlimitedQuota(),
                e.isModelLimitsEnabled(),
                e.getModelLimits() == null ? "" : e.getModelLimits(),
                e.getAllowIps(),
                e.getUsedQuota(),
                e.getGroup(),
                e.isCrossGroupRetry(),
                e.isEndpointLimitsEnabled(),
                e.getEndpointLimits() == null ? "" : e.getEndpointLimits(),
                e.getAccessedTime(),
                e.getCreatedTime());
    }

    /**
     * 将领域 long 用户 id 安全转为 DB INTEGER。
     *
     * <p>DB-SCHEMA §2 user_id 列定义为 INTEGER（与现网整数主键一致），但领域/接口层用 long 接收
     * （AuthenticatedActor.userId 为 long）。本工程主键 BIGSERIAL 在 INTEGER 范围内长期不会溢出，
     * 转换前显式判断越界，溢出即抛出（不静默截断）。</p>
     */
    private int toIntUserId(long userId) {
        if (userId > Integer.MAX_VALUE || userId < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("userId out of INTEGER range: " + userId);
        }
        return (int) userId;
    }

    /** JSONB 列空串归 null（避免 PG 解析空串 JSON 报错；查询出来再归空串）。 */
    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
