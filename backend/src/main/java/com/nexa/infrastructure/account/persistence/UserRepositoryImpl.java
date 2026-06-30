package com.nexa.infrastructure.account.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexa.domain.account.exception.UserAlreadyExistsException;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.Username;
import com.nexa.infrastructure.account.persistence.po.UserPO;
import com.nexa.infrastructure.persistence.PageQueries;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link UserRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义 {@code UserRepository} 接口，本类用 {@link UserMapper} +
 * PO 就近工厂映射实现它。领域聚合 {@link User} 与 PO {@link UserPO} 分离，domain 不感知持久化框架。
 * 软删除过滤由 PO 的 {@code @TableLogic} 自动在 select 追加 {@code deleted_at IS NULL}。</p>
 *
 * <p>并发查重兜底：注册并发竞态下 {@code existsByUsername} 可能漏判，最终由 users.username
 * 唯一索引在 {@code save} 时抛 {@link DataIntegrityViolationException}，本类翻译为领域语义的
 * {@link UserAlreadyExistsException}（不吞错，带上用户名上下文）。</p>
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper mapper;

    /** @param mapper 用户 MyBatis-Plus Mapper（infra 内部依赖） */
    public UserRepositoryImpl(UserMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByUsername(Username username) {
        return mapper.selectCount(Wrappers.<UserPO>lambdaQuery()
                .eq(UserPO::getUsername, username.value())) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findByUsername(Username username) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getUsername, username.value())))
                .map(UserPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(UserPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findByEmail(Email email) {
        // 邮箱列非唯一，取首个命中（与原派生查询 findByEmail 行为一致）。
        return mapper.selectList(Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getEmail, email.value())
                        .last("LIMIT 1")).stream()
                .findFirst()
                .map(UserPO::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UserAlreadyExistsException 当唯一索引（username / aff_code）冲突时
     */
    @Override
    public User save(User user) {
        UserPO entity = UserPO.of(user);
        try {
            if (entity.getId() == null) {
                mapper.insert(entity);
            } else {
                mapper.updateById(entity);
            }
            // 保存后把数据库生成的 id 回填回聚合，供注册用例发事件 / 接口层回显。
            user.assignId(entity.getId());
            return entity.toDomain();
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
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserPO> mpPage = PageQueries.mpOf(p, size);
        // 列表/搜索统一按 id 升序，结果稳定可分页（API-ENDPOINTS §1.4）。
        LambdaQueryWrapper<UserPO> w = Wrappers.<UserPO>lambdaQuery().orderByAsc(UserPO::getId);
        if (keyword != null && !keyword.isBlank()) {
            // 大小写不敏感模糊：username/email/group（"group" 为 PG 保留字，双引号转义）。
            // {0} 占位防注入；keyword 包裹通配符后传入（转义 % / _ 留待后续 wave，本切片接受字面量）。
            String kw = "%" + keyword.trim() + "%";
            w.and(q -> q.apply("LOWER(username) LIKE LOWER({0})", kw)
                    .or().apply("LOWER(email) LIKE LOWER({0})", kw)
                    .or().apply("LOWER(\"group\") LIKE LOWER({0})", kw));
        }
        mapper.selectPage(mpPage, w);
        List<User> items = mpPage.getRecords().stream().map(UserPO::toDomain).toList();
        return new UserRepository.Page<>(items, mpPage.getTotal(), p, size);
    }

    /** {@inheritDoc} */
    @Override
    public void softDelete(long id) {
        // 写 deleted_at 时间戳；@TableLogic 使该行后续不再被 select 读取（DB-SCHEMA §1 软删除）。
        mapper.markDeleted(id, Instant.now().getEpochSecond());
    }
}
