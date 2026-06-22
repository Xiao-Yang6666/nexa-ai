package com.nexa.account.infrastructure.persistence;

import com.nexa.account.domain.exception.UserAlreadyExistsException;
import com.nexa.account.domain.model.User;
import com.nexa.account.domain.repository.UserRepository;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.Role;
import com.nexa.account.domain.vo.UserStatus;
import com.nexa.account.domain.vo.Username;
import com.nexa.account.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link UserRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义 {@code UserRepository} 接口，本类用
 * {@link SpringDataUserJpaRepository} + 聚合↔实体映射实现它（backend-engineer §2.3）。
 * 领域聚合 {@link User} 与 JPA 实体 {@link UserJpaEntity} 分离，映射集中在此处，
 * domain 因此不感知 Hibernate。</p>
 *
 * <p>并发查重兜底：注册并发竞态下 {@code existsByUsername} 可能漏判，最终由 users.username
 * 唯一索引在 {@code save} 时抛 {@link DataIntegrityViolationException}，本类翻译为领域语义的
 * {@link UserAlreadyExistsException}（不吞错，带上用户名上下文）。</p>
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SpringDataUserJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public UserRepositoryImpl(SpringDataUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByUsername(Username username) {
        return jpa.existsByUsername(username.value());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findByUsername(Username username) {
        return jpa.findByUsername(username.value()).map(UserRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findById(long id) {
        return jpa.findById(id).map(UserRepositoryImpl::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(UserRepositoryImpl::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UserAlreadyExistsException 当唯一索引（username / aff_code）冲突时
     */
    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        try {
            UserJpaEntity saved = jpa.saveAndFlush(entity);
            // 保存后把数据库生成的 id 回填回聚合，供注册用例发事件 / 接口层回显。
            user.assignId(saved.getId());
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底：并发查重漏判或 aff_code 碰撞，翻译为领域异常并保留根因（不吞错）。
            throw new UserAlreadyExistsException(
                    user.username() != null ? user.username().value() : "<unknown>");
        }
    }

    /** {@inheritDoc} */
    @Override
    public UserRepository.Page<User> search(String keyword, int page, int pageSize) {
        // 防御式归一：页码从 1 起、页大小有下界，避免非法分页参数打穿 DB。
        int p = Math.max(page, 1);
        int size = Math.max(pageSize, 1);
        // 列表/搜索统一按 id 升序，结果稳定可分页（API-ENDPOINTS §1.4）。
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Direction.ASC, "id"));

        org.springframework.data.domain.Page<UserJpaEntity> result;
        if (keyword == null || keyword.isBlank()) {
            result = jpa.findAllBy(pageable);
        } else {
            // 模糊匹配：调用方提供裸关键词，这里包裹通配符（转义 % / _ 留待后续 wave，本切片接受字面量）。
            result = jpa.searchByKeyword("%" + keyword.trim() + "%", pageable);
        }
        List<User> items = result.getContent().stream()
                .map(UserRepositoryImpl::toDomain)
                .toList();
        return new UserRepository.Page<>(items, result.getTotalElements(), p, size);
    }

    /** {@inheritDoc} */
    @Override
    public void softDelete(long id) {
        // 写 deleted_at 时间戳；@SQLRestriction 使该行后续不再被读取（DB-SCHEMA §1 软删除）。
        jpa.markDeleted(id, Instant.now().getEpochSecond());
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * <p>新用户 {@code created_at} 在此首次落库时打上当前 epoch 秒；已有 id 的更新场景沿用既有时间
     * （此处简化为：仅在 id 为空时设 created_at，更新时由 JPA 合并保留）。</p>
     *
     * @param user 用户聚合
     * @return 待持久化的 JPA 实体
     */
    private static UserJpaEntity toEntity(User user) {
        UserJpaEntity e = new UserJpaEntity();
        e.setId(user.id());
        e.setUsername(user.username().value());
        e.setPassword(user.passwordHash());
        e.setEmail(user.email() == null ? null : user.email().value());
        e.setRole(user.role().code());
        e.setStatus(user.status().code());
        e.setQuota(user.quota());
        e.setAffCode(user.affCode());
        e.setInviterId(user.inviterId());
        e.setLastLoginAt(user.lastLoginAt());
        e.setGroup(user.group());     // F-1013 分组随聚合落库
        e.setRemark(user.remark());   // F-1014 备注随聚合落库
        e.setSetting(user.setting()); // F-1014 个人设置随聚合落库
        if (user.id() == null) {
            // 仅新建时落 created_at；非空数值列给默认 0，避免 NOT NULL/约束在首存时报错。
            e.setCreatedAt(Instant.now().getEpochSecond());
            e.setUsedQuota(0L);
            e.setRequestCount(0);
            e.setAffCount(0);
            e.setAffQuota(0L);
            e.setAffHistoryQuota(0L);
        }
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link User#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的用户聚合
     */
    private static User toDomain(UserJpaEntity e) {
        Email email = (e.getEmail() == null || e.getEmail().isBlank())
                ? null
                : Email.of(e.getEmail());
        // 数值列的 null 兜底（quota/inviterId/lastLoginAt/usedQuota/requestCount/createdAt → 0）
        // 与 group 空白归一 default，统一收敛在 User.Builder 内，这里只做枚举解析与 email 归一。
        return User.builder()
                .id(e.getId())
                .username(Username.of(e.getUsername()))
                .passwordHash(e.getPassword())
                .email(email)
                .role(Role.fromCode(e.getRole() == null ? Role.COMMON.code() : e.getRole()))
                .status(UserStatus.fromCode(e.getStatus() == null ? UserStatus.ENABLED.code() : e.getStatus()))
                .quota(e.getQuota())
                .affCode(e.getAffCode())
                .inviterId(e.getInviterId())
                .lastLoginAt(e.getLastLoginAt())
                .displayName(e.getDisplayName())
                .setting(e.getSetting())
                .group(e.getGroup())
                .remark(e.getRemark())
                .usedQuota(e.getUsedQuota())
                .requestCount(e.getRequestCount() == null ? null : e.getRequestCount().longValue())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
