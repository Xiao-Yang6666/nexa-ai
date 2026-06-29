package com.nexa.infrastructure.growth.account;

import com.nexa.application.growth.port.UserQuotaAccount;
import com.nexa.domain.growth.exception.GrowthUserNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 增长域用户额度账户端口 {@link UserQuotaAccount} 的 JDBC 实现（基础设施层适配器）。
 *
 * <p><b>跨 bounded context 防腐适配器</b>：增长域签到发额度（GR-1）、邀请额度划转（GR-5）需给用户余额
 * 入账（{@code users.quota += amount}），但 {@code users} 表归账号域所有。为不反向依赖账号域聚合/JPA
 * 实体，本适配器用 {@link JdbcTemplate} 直接对 {@code users} 表做<b>原子自增</b>——只触碰 id/quota 两列，
 * 不复用 account 的 {@code UserJpaEntity}（避免 context 耦合与两个 JPA 实体映射同一表在
 * {@code ddl-auto=validate} 下的冲突）。与计费域 {@code JdbcUserQuotaAccount} 同策略。</p>
 *
 * <p>原子性：SQL 级 {@code quota = quota + ?} 而非读-改-写，杜绝并发丢更新；与签到记录写入 / 邀请额度
 * 扣减由用例 {@code @Transactional} 包裹在同一事务内原子提交（PRD GR-1 §5 / GR-5 §3）。</p>
 */
@Component("growthUserQuotaAccount")
public class JdbcUserQuotaAccount implements UserQuotaAccount {

    private final JdbcTemplate jdbcTemplate;

    /** @param jdbcTemplate Spring JDBC 模板（由 JPA starter 数据源自动装配） */
    public JdbcUserQuotaAccount(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@inheritDoc} */
    @Override
    public void credit(long userId, long amount) {
        if (amount == 0L) {
            return; // 零额度入账无副作用，跳过 UPDATE
        }
        // 原子自增；软删除用户（deleted_at 非空）不入账，避免给已删账号加额度。
        int affected = jdbcTemplate.update(
                "UPDATE users SET quota = quota + ? WHERE id = ? AND deleted_at IS NULL",
                amount, userId);
        if (affected == 0) {
            // 不吞错：目标用户不存在/已删是入账前提被破坏的信号，向上抛由接口层翻译。
            throw new GrowthUserNotFoundException(userId);
        }
    }
}
