package com.nexa.billing.domain.model;

import com.nexa.billing.domain.exception.InvalidBillingParameterException;
import com.nexa.billing.domain.exception.RedemptionAlreadyUsedException;
import com.nexa.billing.domain.exception.RedemptionExpiredException;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.billing.domain.vo.RedemptionStatus;

import java.security.SecureRandom;

/**
 * 兑换码聚合根（充血领域模型，DDD 战术完整档）。
 *
 * <p>对齐 DATA-MODEL §6 Redemption / 表 {@code redemptions}，承载 prd-billing BL-4「兑换码兑换
 * （一次性 + 过期 + 已用守卫）」的全部领域规则。<b>充血</b>：兑换的状态机校验（过期/已用守卫）
 * 与置已用作为行为方法挂在本聚合根上（{@link #redeem(long, long)}），应用层只调一次方法 + 存盘，
 * 不在外部散落 {@code if (status == used) ...} 裸判断（backend-engineer §2.2）。</p>
 *
 * <p>一致性边界：兑换码的「校验 → 入账判定 → 置已用」是聚合内不变量，外部不能绕过本聚合直接
 * 改 {@code status}；并发重复兑换的原子性由应用层事务 + 「仅 UNUSED 可 redeem」守卫共同保证
 * （prd-billing BL-4 AC「并发提交两次仅一次成功」）。</p>
 */
public final class Redemption {

    /** 兑换码 Key 字符集（明文 char(32)，DATA-MODEL §6 Key uniqueIndex；用大写+数字便于人工录入）。 */
    private static final String KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** 兑换码 Key 长度（DB-SCHEMA §6 char(32)）。 */
    public static final int KEY_LENGTH = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Long id;
    private final Integer creatorUserId;
    private final String key;
    private RedemptionStatus status;
    private final String name;
    private final Quota quota;
    private final Long createdTime;
    private Long redeemedTime;
    private Integer usedUserId;
    private final Long expiredTime;

    private Redemption(Long id, Integer creatorUserId, String key, RedemptionStatus status,
                       String name, Quota quota, Long createdTime, Long redeemedTime,
                       Integer usedUserId, Long expiredTime) {
        this.id = id;
        this.creatorUserId = creatorUserId;
        this.key = key;
        this.status = status;
        this.name = name;
        this.quota = quota;
        this.createdTime = createdTime;
        this.redeemedTime = redeemedTime;
        this.usedUserId = usedUserId;
        this.expiredTime = expiredTime;
    }

    /**
     * 工厂方法：管理员生成一张新兑换码（prd-billing BL-4 §5 生成侧）。
     *
     * <p>新码状态恒为 {@link RedemptionStatus#UNUSED}、随机生成唯一明文 Key、面额由管理员指定
     * （DATA-MODEL §6 quota default 100）。批量生成由应用层循环调用本工厂 N 次（{@code Count}
     * 仅 API 入参不落库，DB-SCHEMA §6 {@code @Transient}）。</p>
     *
     * @param creatorUserId 创建者用户 id（&gt; 0）
     * @param name          兑换码名称/批次标识（可空）
     * @param quota         面额（quota 单位，须 &gt;= 0）
     * @param expiredTime   过期时间（epoch 秒，{@code 0} 或 {@code null}=永不过期）
     * @param nowEpochSec   当前时间（epoch 秒，由应用层注入，便于测试）
     * @return 新兑换码聚合（未使用态）
     * @throws InvalidBillingParameterException 创建者非法时
     */
    public static Redemption create(int creatorUserId, String name, Quota quota,
                                    Long expiredTime, long nowEpochSec) {
        if (creatorUserId <= 0) {
            throw new InvalidBillingParameterException("redemption creator userId must be positive");
        }
        if (quota == null) {
            throw new InvalidBillingParameterException("redemption quota must not be null");
        }
        return new Redemption(null, creatorUserId, generateKey(), RedemptionStatus.UNUSED,
                name, quota, nowEpochSec, null, null,
                expiredTime == null ? 0L : expiredTime);
    }

    /**
     * 重建工厂（持久化重建方向，由 RepositoryImpl 从 JPA 实体调用）。
     *
     * @param id            主键
     * @param creatorUserId 创建者用户 id
     * @param key           兑换码明文
     * @param status        状态
     * @param name          名称
     * @param quota         面额
     * @param createdTime   创建时间
     * @param redeemedTime  核销时间（可空）
     * @param usedUserId    核销人（可空）
     * @param expiredTime   过期时间（0=不过期）
     * @return 重建的兑换码聚合
     */
    public static Redemption rehydrate(Long id, Integer creatorUserId, String key, RedemptionStatus status,
                                       String name, Quota quota, Long createdTime, Long redeemedTime,
                                       Integer usedUserId, Long expiredTime) {
        // 委托 Builder 装配：字段名自解释、过期时间 null→0（永不过期）的归一收敛在 Builder 一处。
        return builder()
                .id(id)
                .creatorUserId(creatorUserId)
                .key(key)
                .status(status)
                .name(name)
                .quota(quota)
                .createdTime(createdTime)
                .redeemedTime(redeemedTime)
                .usedUserId(usedUserId)
                .expiredTime(expiredTime)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好
     * （兑换码 10 个重建参数里第 7/8/10 个都是 {@code Long} 时间戳，位置参数极易串位，Builder 一目了然）。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>触发生成不变量与状态机，纯还原已存状态。</p>
     *
     * @return 新的兑换码重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 兑换码聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：{@link #expiredTime(Long)} 把 {@code null} 归一为 {@code 0L}（永不过期，
     * 与 {@link #create} 语义一致）——这样 JPA 实体里可空 {@code expired_time} 列的兜底逻辑
     * 收敛在此，{@code RedemptionRepositoryImpl.toDomain} 不再散落 {@code ?:} 三元。
     * 其余可空列（{@code redeemedTime}/{@code usedUserId}）保持 {@code null} 语义（未核销）。</p>
     */
    public static final class Builder {
        private Long id;
        private Integer creatorUserId;
        private String key;
        private RedemptionStatus status;
        private String name;
        private Quota quota;
        private Long createdTime;
        private Long redeemedTime;
        private Integer usedUserId;
        private Long expiredTime = 0L;

        private Builder() {
        }

        /** @param id 主键（未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param creatorUserId 创建者用户 id */
        public Builder creatorUserId(Integer creatorUserId) {
            this.creatorUserId = creatorUserId;
            return this;
        }

        /** @param key 兑换码明文 */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /** @param status 当前状态 */
        public Builder status(RedemptionStatus status) {
            this.status = status;
            return this;
        }

        /** @param name 名称/批次标识，可为 null */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** @param quota 面额额度值对象 */
        public Builder quota(Quota quota) {
            this.quota = quota;
            return this;
        }

        /** @param createdTime 创建时间（epoch 秒） */
        public Builder createdTime(Long createdTime) {
            this.createdTime = createdTime;
            return this;
        }

        /** @param redeemedTime 核销时间（epoch 秒），未核销为 null */
        public Builder redeemedTime(Long redeemedTime) {
            this.redeemedTime = redeemedTime;
            return this;
        }

        /** @param usedUserId 核销人用户 id，未核销为 null */
        public Builder usedUserId(Integer usedUserId) {
            this.usedUserId = usedUserId;
            return this;
        }

        /** @param expiredTime 过期时间（epoch 秒，null 归一为 0=永不过期） */
        public Builder expiredTime(Long expiredTime) {
            this.expiredTime = expiredTime == null ? 0L : expiredTime;
            return this;
        }

        /**
         * 装配并返回重建的兑换码聚合（不触发生成不变量与状态机）。
         *
         * @return 重建的兑换码聚合
         */
        public Redemption build() {
            return new Redemption(id, creatorUserId, key, status, name, quota,
                    createdTime, redeemedTime, usedUserId, expiredTime);
        }
    }

    /**
     * 兑换：守护一次性 + 过期不变量，通过则入账判定并置已用（prd-billing BL-4 §3 主流程）。
     *
     * <p>充血核心：本方法在聚合内逐关校验后<b>改变自身状态</b>（status→USED、写 redeemedTime/usedUserId），
     * 返回应入账的面额由应用层加到用户余额。校验顺序对齐 BL-4 §3：先判已用（rd_used）、再判过期
     * （rd_exp）；不存在/格式错由仓储查不到时在应用层抛 {@code RedemptionInvalidException}（不在本方法）。</p>
     *
     * @param redeemerUserId 兑换人用户 id（&gt; 0）
     * @param nowEpochSec    当前时间（epoch 秒，过期判定基准；由应用层注入）
     * @return 应入账给兑换人的面额额度
     * @throws RedemptionAlreadyUsedException 码已使用/已禁用（BL-4 rd_used-是）
     * @throws RedemptionExpiredException     码已过期（expiredTime != 0 且已到，BL-4 rd_exp-是）
     * @throws InvalidBillingParameterException 兑换人非法
     */
    public Quota redeem(long redeemerUserId, long nowEpochSec) {
        if (redeemerUserId <= 0) {
            throw new InvalidBillingParameterException("redeemer userId must be positive");
        }
        // 守卫一：仅未使用码可兑换（已使用/已禁用均拒，杜绝重复入账，BL-4 rd_used）。
        if (!status.isRedeemable()) {
            throw new RedemptionAlreadyUsedException("redemption code is already used or disabled");
        }
        // 守卫二：过期判定（expiredTime=0 表示永不过期；非 0 且已到则拒，BL-4 rd_exp）。
        if (expiredTime != null && expiredTime != 0L && nowEpochSec >= expiredTime) {
            throw new RedemptionExpiredException("redemption code is expired");
        }
        // 通过校验：聚合内置已用（一次性），写核销时间与核销人（BL-4 rd_mark）。
        this.status = RedemptionStatus.USED;
        this.redeemedTime = nowEpochSec;
        this.usedUserId = (int) redeemerUserId;
        return quota;
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
     * 生成随机唯一兑换码明文（char(32)，大写字母+数字）。
     *
     * <p>用 {@link SecureRandom} 避免可预测码被枚举兑换。唯一性兜底由 DB 唯一索引 + 仓储 save
     * 冲突重试保证（碰撞概率在 36^32 空间下可忽略）。</p>
     *
     * @return 32 位明文兑换码
     */
    private static String generateKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(KEY_ALPHABET.charAt(RANDOM.nextInt(KEY_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ---- 访问器（领域查询，无 setter；状态变更只经行为方法） ----

    /** @return 主键（未持久化时为 null） */
    public Long id() {
        return id;
    }

    /** @return 创建者用户 id */
    public Integer creatorUserId() {
        return creatorUserId;
    }

    /** @return 兑换码明文 */
    public String key() {
        return key;
    }

    /** @return 当前状态 */
    public RedemptionStatus status() {
        return status;
    }

    /** @return 名称/批次标识 */
    public String name() {
        return name;
    }

    /** @return 面额额度 */
    public Quota quota() {
        return quota;
    }

    /** @return 创建时间（epoch 秒） */
    public Long createdTime() {
        return createdTime;
    }

    /** @return 核销时间（epoch 秒，未核销为 null） */
    public Long redeemedTime() {
        return redeemedTime;
    }

    /** @return 核销人用户 id（未核销为 null） */
    public Integer usedUserId() {
        return usedUserId;
    }

    /** @return 过期时间（epoch 秒，0=不过期） */
    public Long expiredTime() {
        return expiredTime;
    }
}
