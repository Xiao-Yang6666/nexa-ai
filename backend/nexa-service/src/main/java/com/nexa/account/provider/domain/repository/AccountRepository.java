package com.nexa.account.provider.domain.repository;

import com.nexa.account.provider.domain.model.Account;
import com.nexa.account.provider.domain.vo.Pagination;

import java.util.List;
import java.util.Optional;

/**
 * 供应商账号领域仓储接口（DDD 依赖倒置：domain 定义，infrastructure 实现）。
 *
 * <p>列表用 {@link Pagination} 值对象表达分页（在 Impl 转 Spring PageRequest）。
 * platform 为可空过滤维度。findSchedulable 供未来调度子系统按可调度态筛选账号。</p>
 */
public interface AccountRepository {

    /**
     * 保存账号（新建 id=null 或更新），并维护 account_groups fan-out。
     *
     * @param account 账号聚合
     * @return 持久化后的账号（含回填 id）
     */
    Account save(Account account);

    /**
     * 按 id 查询账号。
     *
     * @param id 账号 id
     * @return 账号（含分组关联），缺失返回空
     */
    Optional<Account> findById(long id);

    /**
     * 分页 + 平台过滤列出账号。
     *
     * @param platform   平台过滤（可空=不过滤）
     * @param pagination 分页参数
     * @return 当前页账号列表
     */
    List<Account> findPage(String platform, Pagination pagination);

    /**
     * 平台过滤计数（列表 total）。
     *
     * @param platform 平台过滤（可空）
     * @return 总数
     */
    long count(String platform);

    /**
     * 按平台列出账号。
     *
     * @param platform 平台
     * @return 该平台下账号列表
     */
    List<Account> findByPlatform(String platform);

    /**
     * 列出全部账号（不过滤状态），供跨上下文只读集成（模型目录/缺失检测投影账号支持的模型名）。
     *
     * @return 全部账号列表
     */
    List<Account> findAll();

    /**
     * 列出在给定时刻可调度的账号（供调度子系统）。
     *
     * @param now 判定时刻 epoch 秒
     * @return 可调度账号列表
     */
    List<Account> findSchedulable(long now);

    /**
     * 列出某分组下、给定时刻可调度的账号（供转发选 account 取凭证）。
     *
     * <p>先按 account_groups.group 命中该分组的账号，再用领域聚合 {@link Account#isSchedulable(long)}
     * 终判（status=ACTIVE + 未过期 + 未在过载窗）。返回结果按账号优先级升序（小=高优先）+ 组内优先级，
     * 由调用方/适配层决定最终选择策略。</p>
     *
     * @param group 字符串分组（对齐 channel/abilities 的 group）
     * @param now   判定时刻 epoch 秒
     * @return 该分组下可调度的账号列表
     * @deprecated 方案乙：选账号已改为按模型 A 反查（{@link #findSchedulableByModel}），分组只用于售价，
     *             不再参与上游账号调度。保留本方法仅为兼容存量调用，新链路勿用。
     */
    @Deprecated
    List<Account> findSchedulableByGroup(String group, long now);

    /**
     * 列出可服务给定模型 A、且在给定时刻可调度的账号（方案乙：选账号按模型反查，跨全部分组）。
     *
     * <p>用 {@code abilities} 反向索引按模型粗筛 ACTIVE 行 → 去重 accountId → 装配聚合 →
     * 领域 {@link Account#supportsModel(String)} 精确包含校验（剔除 LIKE 子串误命中）→
     * {@link Account#isSchedulable(long)} 终判（ACTIVE+未过期+未过载）→ priority 升序（小=高优先）。
     * 售价分组（modelgroup）与本调度彻底解耦：账号能否被选只取决于「是否声明支持该模型 + 是否可调度」，
     * 不再看分组字符串。</p>
     *
     * @param model 客户请求并解析出的平台模型名 A
     * @param now   判定时刻 epoch 秒
     * @return 可服务该模型且可调度的账号列表（priority 升序）
     */
    List<Account> findSchedulableByModel(String model, long now);

    /**
     * 按 id 删除账号（连带清理 account_groups 关联）。
     *
     * @param id 账号 id
     */
    void deleteById(long id);
}
