package com.nexa.infrastructure.billing.account;

import com.nexa.application.billing.port.UserQuotaAccount;
import com.nexa.domain.billing.exception.InvalidBillingParameterException;
import com.nexa.domain.billing.vo.Quota;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户额度账户端口 {@link UserQuotaAccount} 的 JDBC 实现（基础设施层适配器）。
 *
 * <p><b>跨 bounded context 防腐适配器</b>：计费域需给用户余额入账（{@code users.quota += amount}），
 * 但 {@code users} 表归账号域（com.nexa.account）所有。为不反向依赖账号域聚合/JPA 实体，本适配器
 * 用 {@link JdbcTemplate} 直接对 {@code users} 表做<b>原子自增</b>与读取——只触碰 id/quota 两列，
 * 不复用 account 的 {@code UserJpaEntity}（避免 context 耦合，也避免两个 JPA 实体映射同一张表在
 * {@code ddl-auto=validate} 下的潜在冲突）。</p>
 *
 * <p>原子性：用 SQL 级 {@code quota = quota + ?} 而非「读-改-写」，杜绝并发充值/兑换的丢更新
 * （prd-billing BL-1 pay_credit / BL-4 rd_credit）。本方法与业务单据置态由用例的 {@code @Transactional}
 * 包裹在同一事务内，保证「入账 + 置已用/置 success」原子提交。</p>
 */
@Component("billingJdbcUserQuotaAccount")
public class JdbcUserQuotaAccount implements UserQuotaAccount {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate Spring JDBC 模板（由 JPA starter 的数据源自动装配）
     */
    public JdbcUserQuotaAccount(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** {@inheritDoc} */
    @Override
    public void credit(long userId, Quota amount) {
        if (amount.isZero()) {
            return; // 零额度入账无副作用，直接返回（避免无谓 UPDATE）。
        }
        // 原子自增；软删除用户（deleted_at 非空）不入账，避免给已删账号加额度。
        int affected = jdbcTemplate.update(
                "UPDATE users SET quota = quota + ? WHERE id = ? AND deleted_at IS NULL",
                amount.value(), userId);
        if (affected == 0) {
            // 不吞错：目标用户不存在/已删是入账前提被破坏的信号，向上抛由接口层翻译为 400。
            throw new InvalidBillingParameterException("cannot credit quota: user not found or deleted, id=" + userId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void debit(long userId, Quota amount) {
        if (amount.isZero()) {
            return; // 零额度扣减无副作用，直接返回（避免无谓 UPDATE）。
        }
        // 原子自减；软删除用户不扣减。本期最小结算不做余额下限保护（允许欠费），完整预扣/余额闸待后续。
        int affected = jdbcTemplate.update(
                "UPDATE users SET quota = quota - ? WHERE id = ? AND deleted_at IS NULL",
                amount.value(), userId);
        if (affected == 0) {
            // 不吞错：目标用户不存在/已删是结算前提被破坏的信号，向上抛由调用方处置。
            throw new InvalidBillingParameterException("cannot debit quota: user not found or deleted, id=" + userId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Quota debitToZero(long userId, Quota amount) {
        if (amount.isZero()) {
            return Quota.ZERO;
        }
        // 原子「扣到 0」并用 CTE 捕获扣前/扣后余额：实扣 = 扣前 - 扣后。
        // 软删用户不扣减；无命中行 → 用户不存在/已删，抛错由上层翻译。
        Long actual = jdbcTemplate.query(
                "WITH before AS (SELECT id, quota AS q FROM users WHERE id = ? AND deleted_at IS NULL), "
                        + "upd AS (UPDATE users SET quota = GREATEST(quota - ?, 0) "
                        + "WHERE id = (SELECT id FROM before) RETURNING quota AS q) "
                        + "SELECT before.q - upd.q FROM before, upd",
                rs -> rs.next() ? rs.getLong(1) : null,
                userId, amount.value());
        if (actual == null) {
            throw new InvalidBillingParameterException("cannot debit quota: user not found or deleted, id=" + userId);
        }
        return Quota.of(Math.max(actual, 0L));
    }

    /** {@inheritDoc} */
    @Override
    public Quota balanceOf(long userId) {
        Long balance = jdbcTemplate.query(
                "SELECT quota FROM users WHERE id = ? AND deleted_at IS NULL",
                rs -> rs.next() ? rs.getLong(1) : null,
                userId);
        return balance == null ? Quota.ZERO : Quota.of(balance);
    }
}
