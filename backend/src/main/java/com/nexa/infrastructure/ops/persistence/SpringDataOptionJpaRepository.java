package com.nexa.infrastructure.ops.persistence;

import com.nexa.infrastructure.ops.persistence.po.OptionPO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA 仓库（全站选项，基础设施层内部接口）。
 *
 * <p>仅供 {@link OptionRepositoryImpl} 内部使用，不直接暴露给应用/领域层——领域只认
 * {@code domain.option.OptionRepository}。主键为 String（key）。覆盖式写入用 {@code save}
 * （JPA merge 语义：存在更新、不存在插入，对齐 F-4018 幂等）。</p>
 */
interface SpringDataOptionJpaRepository extends JpaRepository<OptionPO, String> {
}
