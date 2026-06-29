package com.nexa.domain.modelgroup.repository;

import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.vo.AccessPolicy;

import java.util.List;
import java.util.Optional;

/**
 * 模型组聚合仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需持久化能力，不关心 JPA/SQL。应用层用例仅依赖本接口，单测可桩替换。
 * 实现见 {@code infrastructure.persistence.ModelGroupRepositoryImpl}。软删除行由实现层
 * {@code @SQLRestriction} 自动过滤，本接口语义上只见「存活」模型组。</p>
 */
public interface ModelGroupRepository {

    /**
     * 按 id 查找模型组（更新/删除/状态切换/授权前定位）。
     *
     * @param id 模型组主键
     * @return 命中返回聚合，否则空（空 → 应用层抛 ModelGroupNotFoundException）
     */
    Optional<ModelGroup> findById(long id);

    /**
     * 按 code 查找模型组（中继链路按编码选组）。
     *
     * @param code 模型组唯一编码
     * @return 命中返回聚合，否则空
     */
    Optional<ModelGroup> findByCode(String code);

    /**
     * 全量列出存活模型组（管理端列表），按 id 升序。
     *
     * @return 全部存活模型组
     */
    List<ModelGroup> findAll();

    /**
     * 按访问策略过滤存活模型组（如查所有公开组供中继默认放行）。
     *
     * @param accessPolicy 访问策略
     * @return 匹配的模型组列表
     */
    List<ModelGroup> findByAccessPolicy(AccessPolicy accessPolicy);

    /**
     * 按 id 集合批量查存活模型组（解析用户/令牌已授权的私有组）。
     *
     * @param ids 模型组主键集合
     * @return 命中的存活模型组列表
     */
    List<ModelGroup> findByIds(List<Long> ids);

    /**
     * code 冲突探测（创建/更新时校验编码唯一）。
     *
     * @param code      待校验编码
     * @param excludeId 需排除的自身 id（null = 不排除，用于创建）
     * @return 存在他组同 code 返回 {@code true}
     */
    boolean existsByCode(String code, Long excludeId);

    /**
     * 持久化模型组聚合（新增或更新）。新建保存后回填自增 id。
     *
     * @param group 待保存的聚合
     * @return 持久化后的聚合（含 id）
     */
    ModelGroup save(ModelGroup group);

    /**
     * 软删除模型组（写 deleted_at 时间戳，保留历史与配置）。
     *
     * @param id          模型组主键
     * @param nowEpochSec 删除时间（epoch 秒，写入 deleted_at）
     * @return 命中并成功软删返回 {@code true}；id 不存在/已删返回 {@code false}
     */
    boolean softDelete(long id, long nowEpochSec);
}
