package com.nexa.infrastructure.growth.persistence;

import com.nexa.domain.growth.exception.AlreadyCheckedInException;
import com.nexa.domain.growth.exception.GrowthPersistenceException;
import com.nexa.domain.growth.model.Checkin;
import com.nexa.domain.growth.repository.CheckinRepository;
import com.nexa.domain.growth.vo.CheckinDate;
import com.nexa.infrastructure.growth.persistence.po.CheckinPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储 {@link CheckinRepository} 的 JPA 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataCheckinJpaRepository} + 聚合↔实体映射
 * 实现（backend-engineer §2.3）。领域聚合 {@link Checkin} 与 JPA 实体分离，映射集中于此，domain 不感知
 * Hibernate。</p>
 *
 * <p><b>唯一索引冲突 → 领域信号</b>（PRD GR-1 §4）：并发同日重复签到穿过应用层查重后，落库命中复合
 * 唯一索引 {@code idx_user_checkin_date}，JPA 抛 {@link DataIntegrityViolationException}；本类在
 * {@link #save} 内将其<b>转换</b>为领域可识别的 {@link AlreadyCheckedInException}（400「今日已签到」），
 * 而非吞错或泄露 SQL 细节——保证「并发仅一笔成功」且给前端稳定提示。</p>
 */
@Repository
public class CheckinRepositoryImpl implements CheckinRepository {

    private final SpringDataCheckinJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public CheckinRepositoryImpl(SpringDataCheckinJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByUserIdAndDate(long userId, CheckinDate date) {
        return jpa.existsByUserIdAndCheckinDate((int) userId, date.toWire());
    }

    /** {@inheritDoc} */
    @Override
    public Checkin save(Checkin checkin) {
        try {
            CheckinPO saved = jpa.save(toEntity(checkin));
            checkin.assignId(saved.getId());
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            // 复合唯一索引冲突 = 同日已签（并发穿透查重），转为预期业务分支（400），不当作 500 吞。
            throw new AlreadyCheckedInException();
        } catch (RuntimeException ex) {
            // 其余数据访问异常 wrap 带上下文向上抛（不吞错，保留错误链）。
            throw new GrowthPersistenceException("save checkin failed for userId=" + checkin.userId(), ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long countByUserId(long userId) {
        return jpa.countByUserId((int) userId);
    }

    /** {@inheritDoc} */
    @Override
    public long sumQuotaByUserId(long userId) {
        return jpa.sumQuotaByUserId((int) userId);
    }

    /** {@inheritDoc} */
    @Override
    public List<Checkin> findByUserIdAndDateRange(long userId, CheckinDate startDate, CheckinDate endDate) {
        return jpa.findRange((int) userId, startDate.toWire(), endDate.toWire())
                .stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Checkin> findByUserIdAndDate(long userId, CheckinDate date) {
        return jpa.findByUserIdAndCheckinDate((int) userId, date.toWire()).map(this::toDomain);
    }

    // ---- 聚合 <-> 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param c 签到记录聚合
     * @return 待持久化实体
     */
    private CheckinPO toEntity(Checkin c) {
        CheckinPO e = new CheckinPO();
        e.setId(c.id());
        e.setUserId((int) c.userId());
        e.setCheckinDate(c.checkinDate().toWire());
        e.setQuotaAwarded(c.quotaAwarded());
        e.setCreatedAt(c.createdAt());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link Checkin#rehydrate}）。
     *
     * @param e JPA 实体
     * @return 重建的签到记录聚合
     */
    private Checkin toDomain(CheckinPO e) {
        return Checkin.rehydrate(
                e.getId(),
                e.getUserId() == null ? 0L : e.getUserId(),
                CheckinDate.parse(e.getCheckinDate()),
                e.getQuotaAwarded() == null ? 0L : e.getQuotaAwarded(),
                e.getCreatedAt() == null ? 0L : e.getCreatedAt());
    }
}
