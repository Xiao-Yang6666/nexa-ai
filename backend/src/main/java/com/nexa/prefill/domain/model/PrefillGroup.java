package com.nexa.prefill.domain.model;

import com.nexa.prefill.domain.exception.InvalidPrefillParameterException;
import com.nexa.prefill.domain.vo.PrefillItems;
import com.nexa.prefill.domain.vo.PrefillType;

/**
 * 预填分组聚合根（充血领域模型，DDD 轻量档配置子域）。
 *
 * <p>对齐 DB-SCHEMA §17 PrefillGroup / 表 {@code prefill_groups}，承载 PRD 模块十五 §14
 * 「预填分组 CRUD（model/tag/endpoint 三类型）/ 名称冲突校验 / 下拉填充 / 软删除」的领域规则。
 * <b>充血</b>：名称/条目的校验与变更作为行为方法挂在本聚合根上（{@link #rename}、
 * {@link #replaceItems}），应用层只调方法 + 存盘，不在外部散落字段裸赋值
 * （backend-engineer §2.2）。</p>
 *
 * <p>一致性边界：name 不变量（非空、长度 ≤ 64，对齐 DB {@code name varchar(64) not null}）、
 * type 不变量（合法枚举）由本聚合守护；外部不能绕过聚合直接改字段。<b>名称冲突</b>是跨多个分组
 * 的约束（同 type 下唯一），属仓储/应用层职责（需查库），不在本聚合内（聚合只守自身不变量，
 * 不感知其它聚合）。软删除标记 {@code deletedAt} 由仓储在删除时写入，本聚合不持有删除行为。</p>
 */
public final class PrefillGroup {

    /** name 最大长度（对齐 DB-SCHEMA §17 {@code name varchar(64)}）。 */
    public static final int NAME_MAX_LENGTH = 64;

    private Long id;
    private String name;
    private final PrefillType type;
    private PrefillItems items;
    private String description;
    private final Long createdTime;
    private Long updatedTime;

    private PrefillGroup(Long id, String name, PrefillType type, PrefillItems items,
                         String description, Long createdTime, Long updatedTime) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.items = items;
        this.description = description;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
    }

    /**
     * 工厂方法：新建一个预填分组（F-2012 创建，PRD 模块十五 §14）。
     *
     * <p>type 在创建后<b>不可变</b>（业务上一个分组的类型固定，改类型等价新建一个组）；name 与
     * items 可后续经行为方法变更。name 在此做格式校验（非空、长度），<b>名称冲突</b>由应用层在
     * 入库前查同 type 下重名（聚合不感知其它聚合，见类注释）。</p>
     *
     * @param name        分组名称（非空，长度 ≤ 64）
     * @param type        分组类型（model/tag/endpoint）
     * @param items       条目集合（可空，等价空集合）
     * @param description 描述（可空，长度 ≤ 255）
     * @param nowEpochSec 当前时间（epoch 秒，由应用层注入，便于测试）
     * @return 新建的预填分组聚合（未持久化，id 为 null）
     * @throws InvalidPrefillParameterException 当 name / type 非法时
     */
    public static PrefillGroup create(String name, PrefillType type, PrefillItems items,
                                      String description, long nowEpochSec) {
        if (type == null) {
            throw new InvalidPrefillParameterException("prefill type is required");
        }
        String validName = validateName(name);
        String validDesc = validateDescription(description);
        PrefillItems validItems = items == null ? PrefillItems.EMPTY : items;
        return new PrefillGroup(null, validName, type, validItems, validDesc, nowEpochSec, nowEpochSec);
    }

    /**
     * 重建工厂（持久化重建方向，由 RepositoryImpl 从 JPA 实体调用）。
     *
     * <p>不走 {@link #create} 的业务校验（库中数据视为已合法），直接装配字段。</p>
     *
     * @param id          主键
     * @param name        名称
     * @param type        类型
     * @param items       条目集合
     * @param description 描述
     * @param createdTime 创建时间（epoch 秒）
     * @param updatedTime 更新时间（epoch 秒）
     * @return 重建的预填分组聚合
     */
    public static PrefillGroup rehydrate(Long id, String name, PrefillType type, PrefillItems items,
                                         String description, Long createdTime, Long updatedTime) {
        return new PrefillGroup(id, name, type, items, description, createdTime, updatedTime);
    }

    /**
     * 重命名（F-2013 更新，PRD 模块十五 §14「名称冲突校验」格式侧）。
     *
     * <p>充血：聚合内校验新 name 格式后改自身字段并刷新 updatedTime。<b>名称冲突</b>（新 name 与
     * 同 type 他组重名）由应用层在调用本方法前查库判定，不在此（聚合不感知其它聚合）。
     * 入参为 null 表示不改名（更新请求可只改 items）。</p>
     *
     * @param newName     新名称（null = 保持原名；非 null 时须非空且 ≤ 64）
     * @param nowEpochSec 当前时间（epoch 秒）
     * @throws InvalidPrefillParameterException 当 newName 非 null 但格式非法时
     */
    public void rename(String newName, long nowEpochSec) {
        if (newName == null) {
            return;
        }
        this.name = validateName(newName);
        this.updatedTime = nowEpochSec;
    }

    /**
     * 整体替换条目集合（F-2013 更新 items，PRD 模块十五 §14）。
     *
     * <p>充血：以新集合整体覆盖（下拉预填语义是「这个组当前包含哪些条目」，整体替换而非增量），
     * 入参 null 表示不改条目。条目规范化已在 {@link PrefillItems} 构造期完成。</p>
     *
     * @param newItems    新条目集合（null = 保持原条目）
     * @param nowEpochSec 当前时间（epoch 秒）
     */
    public void replaceItems(PrefillItems newItems, long nowEpochSec) {
        if (newItems == null) {
            return;
        }
        this.items = newItems;
        this.updatedTime = nowEpochSec;
    }

    /**
     * 回填数据库生成的主键（save 后调用）。
     *
     * @param assignedId 数据库自增 id
     */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    /**
     * 校验并规范化 name（非空、去首尾空白、长度 ≤ 64）。
     *
     * @param name 原始名称
     * @return 规范化后的名称
     * @throws InvalidPrefillParameterException name 为空或超长
     */
    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidPrefillParameterException("prefill group name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new InvalidPrefillParameterException(
                    "prefill group name too long (max " + NAME_MAX_LENGTH + ")");
        }
        return trimmed;
    }

    /**
     * 校验并规范化 description（可空、长度 ≤ 255，对齐 DB {@code description varchar(255)}）。
     *
     * @param description 原始描述
     * @return 规范化后的描述（null 透传）
     * @throws InvalidPrefillParameterException 超长
     */
    private static String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 255) {
            throw new InvalidPrefillParameterException("prefill group description too long (max 255)");
        }
        return trimmed;
    }

    // ---- 访问器（领域查询，无 setter；状态变更只经行为方法） ----

    /** @return 主键（未持久化时为 null） */
    public Long id() {
        return id;
    }

    /** @return 分组名称 */
    public String name() {
        return name;
    }

    /** @return 分组类型 */
    public PrefillType type() {
        return type;
    }

    /** @return 条目集合 */
    public PrefillItems items() {
        return items;
    }

    /** @return 描述（可空） */
    public String description() {
        return description;
    }

    /** @return 创建时间（epoch 秒） */
    public Long createdTime() {
        return createdTime;
    }

    /** @return 更新时间（epoch 秒） */
    public Long updatedTime() {
        return updatedTime;
    }
}
