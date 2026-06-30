package com.nexa.infrastructure.growth.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
 * 领域仓储 {@link CheckinRepository} 的 MyBatis-Plus 实现（基础设施层适配器）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link CheckinMapper} + PO 就近工厂方法
 * （{@code CheckinPO.of} / {@code po.toDomain}）实现。领域聚合 {@link Checkin} 与 PO 分离，
 * domain 不感知持久化框架。</p>
 *
 * <p><b>唯一索引冲突 → 领域信号</b>（PRD GR-1 §4）：并发同日重复签到穿过应用层查重后，落库命中复合
 * 唯一索引 {@code idx_user_checkin_date}，抛 {@link DataIntegrityViolationException}；本类在
 * {@link #save} 内将其<b>转换</b>为领域可识别的 {@link AlreadyCheckedInException}（400「今日已签到」），
 * 而非吞错或泄露 SQL 细节——保证「并发仅一笔成功」且给前端稳定提示。</p>
 */
@Repository
public class CheckinRepositoryImpl implements CheckinRepository {

    private final CheckinMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public CheckinRepositoryImpl(CheckinMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByUserIdAndDate(long userId, CheckinDate date) {
        LambdaQueryWrapper<CheckinPO> w = Wrappers.<CheckinPO>lambdaQuery()
                .eq(CheckinPO::getUserId, (int) userId)
                .eq(CheckinPO::getCheckinDate, date.toWire());
        return mapper.selectCount(w) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public Checkin save(Checkin checkin) {
        try {
            CheckinPO po = CheckinPO.of(checkin);
            mapper.insert(po);              // 回填自增 id 到 po
            checkin.assignId(po.getId());
            return po.toDomain();
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
        LambdaQueryWrapper<CheckinPO> w = Wrappers.<CheckinPO>lambdaQuery()
                .eq(CheckinPO::getUserId, (int) userId);
        return mapper.selectCount(w);
    }

    /** {@inheritDoc} */
    @Override
    public long sumQuotaByUserId(long userId) {
        return mapper.sumQuotaByUserId((int) userId);
    }

    /** {@inheritDoc} */
    @Override
    public List<Checkin> findByUserIdAndDateRange(long userId, CheckinDate startDate, CheckinDate endDate) {
        // checkin_date 为定长 YYYY-MM-DD 字符串，字典序等同日期序，闭区间 + 日期降序（PRD GR-2 §5）。
        LambdaQueryWrapper<CheckinPO> w = Wrappers.<CheckinPO>lambdaQuery()
                .eq(CheckinPO::getUserId, (int) userId)
                .ge(CheckinPO::getCheckinDate, startDate.toWire())
                .le(CheckinPO::getCheckinDate, endDate.toWire())
                .orderByDesc(CheckinPO::getCheckinDate);
        return mapper.selectList(w).stream().map(CheckinPO::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Checkin> findByUserIdAndDate(long userId, CheckinDate date) {
        LambdaQueryWrapper<CheckinPO> w = Wrappers.<CheckinPO>lambdaQuery()
                .eq(CheckinPO::getUserId, (int) userId)
                .eq(CheckinPO::getCheckinDate, date.toWire());
        return Optional.ofNullable(mapper.selectOne(w)).map(CheckinPO::toDomain);
    }
}
