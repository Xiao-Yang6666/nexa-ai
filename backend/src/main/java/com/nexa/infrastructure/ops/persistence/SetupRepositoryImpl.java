package com.nexa.infrastructure.ops.persistence;

import com.nexa.domain.ops.setup.SetupMarker;
import com.nexa.domain.ops.setup.SetupRepository;
import com.nexa.infrastructure.ops.persistence.po.SetupPO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 领域仓储 {@link SetupRepository} 的 JPA 实现（基础设施层适配器，F-4015/F-4016）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataSetupJpaRepository} 实现。
 * 单行哨兵 + 固定主键，{@link #saveIfAbsent} 用「先查后插 + 主键唯一兜底」实现并发安全的
 * 初始化幂等护栏：常态用存在性判断快速短路，并发竞态由 DB 主键唯一约束兜底（捕获唯一冲突
 * 返回 false，不让第二个并发请求误以为初始化成功）。</p>
 */
@Repository
public class SetupRepositoryImpl implements SetupRepository {

    private final SpringDataSetupJpaRepository jpa;

    /**
     * @param jpa Spring Data JPA 仓库（infra 内部依赖）
     */
    public SetupRepositoryImpl(SpringDataSetupJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SetupMarker> find() {
        return jpa.findById(SetupMarker.SINGLETON_ID).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInitialized() {
        return jpa.existsById(SetupMarker.SINGLETON_ID);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean saveIfAbsent(SetupMarker marker) {
        // 常态短路：已存在标记即视为已初始化，不再插入（幂等）。
        if (jpa.existsById(marker.id())) {
            return false;
        }
        try {
            jpa.save(new SetupPO(marker.id(), marker.version(), marker.initializedAt()));
            return true;
        } catch (DataIntegrityViolationException e) {
            // 并发竞态：两个请求同时通过 existsById 检查后抢插，DB 主键唯一约束让其一失败。
            // 失败方视为「未由我创建」，返回 false，由上层翻成「系统已经初始化完成」（不吞错语义保留）。
            return false;
        }
    }

    private SetupMarker toDomain(SetupPO e) {
        return SetupMarker.rehydrate(
                e.getId(),
                e.getVersion(),
                e.getInitializedAt() == null ? 0L : e.getInitializedAt());
    }
}
