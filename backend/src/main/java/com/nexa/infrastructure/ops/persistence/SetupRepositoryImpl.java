package com.nexa.infrastructure.ops.persistence;

import com.nexa.infrastructure.ops.persistence.mapper.SetupMapper;

import com.nexa.domain.ops.setup.SetupMarker;
import com.nexa.domain.ops.setup.SetupRepository;
import com.nexa.infrastructure.ops.persistence.po.SetupPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 领域仓储 {@link SetupRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-4015/F-4016）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SetupMapper} + PO 就近工厂方法
 * （{@code PO.of} / {@code po.toDomain}）实现。单行哨兵 + 固定主键，{@link #saveIfAbsent} 用
 * 「先查后插 + 主键唯一兜底」实现并发安全的初始化幂等护栏：常态用存在性判断快速短路，并发竞态由
 * DB 主键唯一约束兜底（捕获唯一冲突返回 false，不让第二个并发请求误以为初始化成功）。</p>
 */
@Repository
public class SetupRepositoryImpl implements SetupRepository {

    private final SetupMapper mapper;

    /**
     * @param mapper MyBatis-Plus Mapper（infra 内部依赖）
     */
    public SetupRepositoryImpl(SetupMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SetupMarker> find() {
        return Optional.ofNullable(mapper.selectById(SetupMarker.SINGLETON_ID)).map(SetupPO::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInitialized() {
        return mapper.selectById(SetupMarker.SINGLETON_ID) != null;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean saveIfAbsent(SetupMarker marker) {
        // 常态短路：已存在标记即视为已初始化，不再插入（幂等）。
        if (mapper.selectById(marker.id()) != null) {
            return false;
        }
        try {
            mapper.insert(SetupPO.of(marker));
            return true;
        } catch (DataIntegrityViolationException e) {
            // 并发竞态：两个请求同时通过存在性检查后抢插，DB 主键唯一约束让其一失败。
            // 失败方视为「未由我创建」，返回 false，由上层翻成「系统已经初始化完成」（不吞错语义保留）。
            return false;
        }
    }
}
