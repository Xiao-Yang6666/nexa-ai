package com.nexa.domain.billing.model;

import com.nexa.domain.billing.exception.InvalidBillingParameterException;
import com.nexa.domain.billing.vo.Money;
import com.nexa.domain.billing.vo.PaymentStatus;
import com.nexa.domain.billing.vo.Quota;

/**
 * 充值订单聚合根（充血领域模型，DDD 战术完整档）。
 *
 * <p>对齐 DATA-MODEL §7 TopUp / 表 {@code top_ups}，承载 prd-billing BL-1「在线充值入账
 * （支付下单 → 回调 → 幂等入账）」的领域规则。<b>充血</b>：两段式资金流的状态机（pending→success）
 * 与幂等守卫作为行为方法挂在本聚合根上（{@link #markPaid()}），应用层只调方法 + 存盘
 * （backend-engineer §2.2）。</p>
 *
 * <p>核心不变量（BL-1）：额度入账只发生在回调验签通过且订单首次置 success 时；以 {@code tradeNo}
 * 做幂等键——重复回调（订单已 success）不重复加额度。验签由基础设施层在调用 {@code markPaid} 前完成，
 * 验签失败的伪造回调根本不进入本聚合（BL-1 pay_drop）。</p>
 */
public final class TopUp {

    private Long id;
    private final Integer userId;
    private final Quota amount;
    private final Money money;
    private final String tradeNo;
    private final String paymentMethod;
    private final String paymentProvider;
    private final Long createTime;
    private Long completeTime;
    private PaymentStatus status;

    private TopUp(Long id, Integer userId, Quota amount, Money money, String tradeNo,
                  String paymentMethod, String paymentProvider, Long createTime,
                  Long completeTime, PaymentStatus status) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.money = money;
        this.tradeNo = tradeNo;
        this.paymentMethod = paymentMethod;
        this.paymentProvider = paymentProvider;
        this.createTime = createTime;
        this.completeTime = completeTime;
        this.status = status;
    }

    /**
     * 工厂方法：用户发起充值下单（prd-billing BL-1 §3 pay_order，创建 pending 单）。
     *
     * <p>下单只创建本地 {@code TopUp} 单（status=pending）写全局唯一商户订单号 {@code tradeNo}，
     * 不入账（额度入账只在回调验签通过后，BL-1 §1）。合规闸门（C7）在应用层/上游已过，本工厂只
     * 守护数据不变量（userId 正、金额/额度非负、tradeNo 非空）。</p>
     *
     * @param userId          充值用户 id（&gt; 0）
     * @param amount          充值额度（quota 单位，入账目标）
     * @param money           支付金额（真实货币）
     * @param tradeNo         全局唯一商户订单号（幂等键，非空）
     * @param paymentMethod   支付方式（stripe/creem/waffo/waffo_pancake/balance）
     * @param paymentProvider 支付渠道（epay/stripe/...）
     * @param nowEpochSec     当前时间（epoch 秒）
     * @return 新充值订单聚合（pending 态）
     * @throws InvalidBillingParameterException 入参非法时
     */
    public static TopUp createOrder(int userId, Quota amount, Money money, String tradeNo,
                                    String paymentMethod, String paymentProvider, long nowEpochSec) {
        if (userId <= 0) {
            throw new InvalidBillingParameterException("topup userId must be positive");
        }
        if (amount == null || money == null) {
            throw new InvalidBillingParameterException("topup amount/money must not be null");
        }
        if (tradeNo == null || tradeNo.isBlank()) {
            throw new InvalidBillingParameterException("topup tradeNo must not be blank");
        }
        return new TopUp(null, userId, amount, money, tradeNo,
                paymentMethod, paymentProvider, nowEpochSec, null, PaymentStatus.PENDING);
    }

    /**
     * 重建工厂（持久化重建方向）。
     *
     * @param id              主键
     * @param userId          用户 id
     * @param amount          充值额度
     * @param money           支付金额
     * @param tradeNo         订单号
     * @param paymentMethod   支付方式
     * @param paymentProvider 支付渠道
     * @param createTime      创建时间
     * @param completeTime    完成时间（可空）
     * @param status          状态
     * @return 重建的充值订单聚合
     */
    public static TopUp rehydrate(Long id, Integer userId, Quota amount, Money money, String tradeNo,
                                  String paymentMethod, String paymentProvider, Long createTime,
                                  Long completeTime, PaymentStatus status) {
        // 委托 Builder 装配：字段名自解释，避免 10 个重建参数（含两段 String 支付方式/渠道）位置串位。
        return builder()
                .id(id)
                .userId(userId)
                .amount(amount)
                .money(money)
                .tradeNo(tradeNo)
                .paymentMethod(paymentMethod)
                .paymentProvider(paymentProvider)
                .createTime(createTime)
                .completeTime(completeTime)
                .status(status)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好
     * （{@code paymentMethod} 与 {@code paymentProvider} 两个相邻 {@code String} 极易串位，
     * Builder 一目了然）。与 {@code rehydrate} 一致——本入口<b>不</b>触发下单不变量与状态机，
     * 纯还原已存状态。</p>
     *
     * @return 新的充值订单重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 充值订单聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：值对象 {@code amount}/{@code money}、枚举 {@code status}、可空时间戳
     * {@code completeTime}（未完成为 null）均原样装配，由调用方 {@code TopUpRepositoryImpl.toDomain}
     * 在构造值对象时完成空值兜底（{@code Quota.of}/{@code Money.of}）。</p>
     */
    public static final class Builder {
        private Long id;
        private Integer userId;
        private Quota amount;
        private Money money;
        private String tradeNo;
        private String paymentMethod;
        private String paymentProvider;
        private Long createTime;
        private Long completeTime;
        private PaymentStatus status;

        private Builder() {
        }

        /** @param id 主键（未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param userId 充值用户 id */
        public Builder userId(Integer userId) {
            this.userId = userId;
            return this;
        }

        /** @param amount 充值额度值对象（入账目标） */
        public Builder amount(Quota amount) {
            this.amount = amount;
            return this;
        }

        /** @param money 支付金额值对象（真实货币） */
        public Builder money(Money money) {
            this.money = money;
            return this;
        }

        /** @param tradeNo 商户订单号（幂等键） */
        public Builder tradeNo(String tradeNo) {
            this.tradeNo = tradeNo;
            return this;
        }

        /** @param paymentMethod 支付方式 */
        public Builder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        /** @param paymentProvider 支付渠道 */
        public Builder paymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        /** @param createTime 创建时间（epoch 秒） */
        public Builder createTime(Long createTime) {
            this.createTime = createTime;
            return this;
        }

        /** @param completeTime 完成时间（epoch 秒），未完成为 null */
        public Builder completeTime(Long completeTime) {
            this.completeTime = completeTime;
            return this;
        }

        /** @param status 当前支付状态 */
        public Builder status(PaymentStatus status) {
            this.status = status;
            return this;
        }

        /**
         * 装配并返回重建的充值订单聚合（不触发下单不变量与状态机）。
         *
         * @return 重建的充值订单聚合
         */
        public TopUp build() {
            return new TopUp(id, userId, amount, money, tradeNo, paymentMethod,
                    paymentProvider, createTime, completeTime, status);
        }
    }

    /**
     * 标记支付成功（回调验签通过后调用，prd-billing BL-1 §3 pay_credit）。
     *
     * <p><b>幂等核心</b>：
     * <ul>
     *   <li>订单已 success（重复回调）→ 返回 {@code false}，<b>不</b>改状态、<b>不</b>触发入账
     *       （BL-1 pay_idem-是 / pay_ok2，AC「同一 TradeNo 回调两次仅增加一次 Amount」）；</li>
     *   <li>订单首次（pending）→ 置 success、写完成时间、返回 {@code true}（应用层据此给用户加额度，
     *       BL-1 pay_credit）。</li>
     * </ul>
     * 返回布尔由应用层据以决定是否调用入账，把「是否首次有效回调」的判定收口在聚合内，杜绝外部
     * 重复入账。</p>
     *
     * @param nowEpochSec 回调到达时间（epoch 秒，写 completeTime）
     * @return {@code true} 表示首次有效入账（应入账）；{@code false} 表示重复回调（已入账过，跳过）
     */
    public boolean markPaid(long nowEpochSec) {
        if (status.isPaid()) {
            // 幂等：已成功的订单重复回调，不重复入账（BL-1 pay_idem-是）。
            return false;
        }
        this.status = PaymentStatus.SUCCESS;
        this.completeTime = nowEpochSec;
        return true;
    }

    /**
     * 回填数据库生成的主键（save 后调用）。
     *
     * @param assignedId 数据库自增 id
     */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    // ---- 访问器（领域查询，无 setter；状态变更只经 markPaid） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 用户 id */
    public Integer userId() {
        return userId;
    }

    /** @return 充值额度（入账目标） */
    public Quota amount() {
        return amount;
    }

    /** @return 支付金额（真实货币） */
    public Money money() {
        return money;
    }

    /** @return 商户订单号（幂等键） */
    public String tradeNo() {
        return tradeNo;
    }

    /** @return 支付方式 */
    public String paymentMethod() {
        return paymentMethod;
    }

    /** @return 支付渠道 */
    public String paymentProvider() {
        return paymentProvider;
    }

    /** @return 创建时间（epoch 秒） */
    public Long createTime() {
        return createTime;
    }

    /** @return 完成时间（epoch 秒，未完成为 null） */
    public Long completeTime() {
        return completeTime;
    }

    /** @return 当前状态 */
    public PaymentStatus status() {
        return status;
    }
}
