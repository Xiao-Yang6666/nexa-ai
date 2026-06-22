package com.nexa.routing.domain.repository;

import com.nexa.routing.domain.model.AffinityRule;
import com.nexa.routing.domain.vo.AffinitySettings;

import java.util.List;
import java.util.Optional;

/**
 * 亲和规则仓储接口（领域层定义，基础设施层实现，F-2031）。
 *
 * <p>DDD 依赖倒置：domain 只声明所需配置读写能力（规则 CRUD / settings 读写），不关心配置源
 * （backend-engineer §2.3）。实现见 {@code infrastructure.config.*}。</p>
 *
 * <p>现网 FC-068 为内存单例 + YAML 配置文件；本项目简化为 DB 表 + JPA（规则配置为持久化，
 * 允许运行时动态配 CRUD 不需重启，与 Channel/Token 等其他配置同构）。</p>
 */
public interface AffinityRuleRepository {

    /**
     * 保存（新增或更新）亲和规则。
     *
     * <p>按规则名唯一键：存在则覆盖，不存在则新增。</p>
     *
     * @param rule 待保存规则
     */
    void save(AffinityRule rule);

    /**
     * 按名查规则。
     *
     * @param name 规则名
     * @return 命中返回规则，否则空
     */
    Optional<AffinityRule> findByName(String name);

    /**
     * 查询全部已启用规则（F-2029，选渠主干用它按序匹配）。
     *
     * <p>返回顺序：内置规则（codex/claude）优先，其余按名称字典序（保证稳定顺序，避免匹配结果飘忽）。</p>
     *
     * @return 已启用规则列表（不可变）
     */
    List<AffinityRule> findEnabledRules();

    /**
     * 查询全部规则（F-2031 配置侧列表展示，含禁用规则）。
     *
     * <p>返回顺序同 {@link #findEnabledRules}。</p>
     *
     * @return 全部规则列表（不可变）
     */
    List<AffinityRule> findAll();

    /**
     * 删除规则（自定义规则允许删除；内置规则删除请求应在 application 层被拦截，返回 400）。
     *
     * @param name 规则名
     */
    void delete(String name);

    /**
     * 读取亲和缓存全局策略（F-2031，对应 openapi GET /api/option 的 channel_affinity_* keys）。
     *
     * @return settings（未配置返回缺省值 {@link AffinitySettings#defaults()}）
     */
    AffinitySettings loadSettings();

    /**
     * 写入亲和缓存全局策略（F-2031，对应 openapi PUT /api/option 的 channel_affinity_* keys）。
     *
     * @param settings 新策略
     */
    void saveSettings(AffinitySettings settings);
}
