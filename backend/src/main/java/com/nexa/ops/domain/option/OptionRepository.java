package com.nexa.ops.domain.option;

import java.util.List;
import java.util.Optional;

/**
 * 全站选项仓储（领域接口，DDD 依赖倒置——domain 定接口，infrastructure 实现）。
 *
 * <p>承载 options 表（KV）的领域级读写契约。应用/领域层只依赖本接口，不感知 JPA/SQL
 * （backend-engineer §2.3）。用于 F-4017 列表、F-4018 单键覆盖写、F-4031 迁移读旧键删旧键。</p>
 */
public interface OptionRepository {

    /**
     * 全量加载所有选项（F-4017 列表查询源；F-4031 迁移读 AllOption）。
     *
     * @return 全部选项（顺序不保证；列表查询的敏感键剔除在用例/视图层做）
     */
    List<Option> findAll();

    /**
     * 按键查询单个选项。
     *
     * @param key 键名
     * @return 命中选项（可空）
     */
    Optional<Option> findByKey(String key);

    /**
     * 覆盖式写入单个选项（F-4018 PUT 幂等：存在则更新、不存在则插入）。
     *
     * @param option 选项
     */
    void save(Option option);

    /**
     * 删除选项键（F-4031 迁移删旧键）。
     *
     * @param key 键名
     */
    void deleteByKey(String key);
}
