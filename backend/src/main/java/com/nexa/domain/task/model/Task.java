package com.nexa.domain.task.model;

import com.nexa.domain.task.exception.InvalidTaskParameterException;
import com.nexa.domain.task.vo.BillingContext;
import com.nexa.domain.task.vo.RefundResult;
import com.nexa.domain.task.vo.TaskPlatform;
import com.nexa.domain.task.vo.TaskStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务聚合根（充血领域模型，DDD 战术完整档，异步任务中心核心子域 F-2001~F-2011）。
 *
 * <p>对齐 DB-SCHEMA §9 Task / 表 {@code tasks}，承载 PRD FL-asynctask AT-1~AT-4 的领域规则：
 * 状态机流转（NOT_START→…→SUCCESS/FAILURE）、CAS 条件更新守护、终态退款/差额结算、超时判定、
 * 产物 URL 回退。<b>充血</b>：状态推进/终态判定/退款重算作为行为方法挂在聚合上
 * （{@link #advanceTo}、{@link #markSuccess}、{@link #markFailure}、{@link #settleRefund}），
 * 应用层只调方法 + 经仓储 CAS 存盘，不在外部散落字段裸赋值（backend-engineer §2.2）。</p>
 *
 * <p><b>一致性边界</b>：status 不变量（终态不可逆、只能合法转换，PRD AT-1）、必填字段
 * （taskId/platform/userId）由本聚合守护；外部不能绕过聚合直接改状态。<b>CAS</b> 是跨并发进程
 * 的乐观锁约束，属仓储层职责（{@code updateWithStatus(fromStatus,...)} 以 fromStatus 为 WHERE 守卫），
 * 聚合只提供「期望的 from 状态」（{@link #statusBeforeAdvance}）供仓储 CAS 用，不感知并发细节。</p>
 *
 * <p><b>退款/差额结算</b>（F-2009）：终态时读 {@link BillingContext} 按规则重算——按次计费
 * （PerCallBilling=true）跳过；失败/超时全额退预扣；成功按真实 token 差额结算（多退少不补）。
 * 结算逻辑作为领域行为 {@link #settleRefund} 内聚于聚合（依据 PRD AT-4 分流树）。</p>
 *
 * <p><b>敏感字段</b>：{@code privateData} 含上游 Key（Gemini/VertexAi ApiKey），禁止下发用户
 * （PRD AT-4 §Key 禁返）；客户视图 DTO 在接口层裁剪，本聚合提供 {@link #resultUrl} 等安全产物访问器。</p>
 */
public final class Task {

    /** 合法状态转换图（PRD AT-1 状态机，from → 可达 to 集合，终态无出边）。 */
    private static final Map<TaskStatus, Set<TaskStatus>> LEGAL_TRANSITIONS = Map.of(
            TaskStatus.NOT_START, EnumSet.of(TaskStatus.SUBMITTED, TaskStatus.QUEUED,
                    TaskStatus.IN_PROGRESS, TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.UNKNOWN),
            TaskStatus.SUBMITTED, EnumSet.of(TaskStatus.QUEUED, TaskStatus.IN_PROGRESS,
                    TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.UNKNOWN),
            TaskStatus.QUEUED, EnumSet.of(TaskStatus.IN_PROGRESS,
                    TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.UNKNOWN),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.UNKNOWN),
            // UNKNOWN 可被后续识别为任意非初始态（上游回报修正）。
            TaskStatus.UNKNOWN, EnumSet.of(TaskStatus.QUEUED, TaskStatus.IN_PROGRESS,
                    TaskStatus.SUCCESS, TaskStatus.FAILURE),
            // 终态无出边。
            TaskStatus.SUCCESS, EnumSet.noneOf(TaskStatus.class),
            TaskStatus.FAILURE, EnumSet.noneOf(TaskStatus.class));

    private Long id;
    private final String taskId;
    private final TaskPlatform platform;
    private final Integer userId;
    private final String group;
    private final Integer channelId;
    private final Long quota;
    private final String action;

    private TaskStatus status;
    private String failReason;
    private final Long submitTime;
    private Long startTime;
    private Long finishTime;
    private String progress;

    // JSON 字段（properties=公开元信息，data=产物，privateData=含 Key 隐私不下发）。
    private String properties;
    private String data;
    private String privateData;

    private final BillingContext billingContext;

    private final Long createdAt;
    private Long updatedAt;

    /** 进入 advanceTo 前的状态快照，供仓储 CAS 的 fromStatus WHERE 守卫使用。 */
    private TaskStatus statusBeforeAdvance;

    private Task(Long id, String taskId, TaskPlatform platform, Integer userId, String group,
                 Integer channelId, Long quota, String action, TaskStatus status, String failReason,
                 Long submitTime, Long startTime, Long finishTime, String progress,
                 String properties, String data, String privateData, BillingContext billingContext,
                 Long createdAt, Long updatedAt) {
        this.id = id;
        this.taskId = taskId;
        this.platform = platform;
        this.userId = userId;
        this.group = group;
        this.channelId = channelId;
        this.quota = quota;
        this.action = action;
        this.status = status;
        this.failReason = failReason;
        this.submitTime = submitTime;
        this.startTime = startTime;
        this.finishTime = finishTime;
        this.progress = progress;
        this.properties = properties;
        this.data = data;
        this.privateData = privateData;
        this.billingContext = billingContext;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.statusBeforeAdvance = status;
    }

    /**
     * 工厂方法：初始化一个新任务（PRD AT-1 InitTask，F-2001/F-2005/F-2007/F-2008）。
     *
     * <p>relay 提交成功后以 {@code status=NOT_START、progress="0%"} 落库。必填字段
     * （taskId/platform/action）在此校验。计费上下文（预扣额度/计费来源）由应用层从提交链路注入。</p>
     *
     * @param taskId         上游任务 ID（非空）
     * @param platform       任务平台（非空）
     * @param userId         归属用户 id（非空）
     * @param group          用户分组（可空）
     * @param channelId      渠道 id（可空，提交时可能未定）
     * @param quota          预扣配额（整数额度单位）
     * @param action         任务动作（如 IMAGINE/MUSIC，非空）
     * @param billingContext 计费上下文（退款依据）
     * @param nowEpochSec    当前时间（epoch 秒，应用层注入便于测试）
     * @return 新建的任务聚合（未持久化，id 为 null，status=NOT_START）
     * @throws InvalidTaskParameterException 必填字段缺失时
     */
    public static Task initTask(String taskId, TaskPlatform platform, Integer userId, String group,
                                Integer channelId, long quota, String action,
                                BillingContext billingContext, long nowEpochSec) {
        if (taskId == null || taskId.isBlank()) {
            throw InvalidTaskParameterException.required("task_id");
        }
        if (platform == null) {
            throw InvalidTaskParameterException.required("platform");
        }
        if (userId == null) {
            throw InvalidTaskParameterException.required("user_id");
        }
        if (action == null || action.isBlank()) {
            throw InvalidTaskParameterException.required("action");
        }
        return new Task(null, taskId.trim(), platform, userId, group, channelId, quota, action.trim(),
                TaskStatus.NOT_START, null, nowEpochSec, null, null, "0%",
                null, null, null, billingContext, nowEpochSec, nowEpochSec);
    }

    /**
     * 重建工厂（持久化重建方向，由 RepositoryImpl 从 JPA 实体调用，不走业务校验）。
     *
     * @param id             主键
     * @param taskId         任务 ID
     * @param platform       平台
     * @param userId         用户 id
     * @param group          分组
     * @param channelId      渠道 id
     * @param quota          配额
     * @param action         动作
     * @param status         状态
     * @param failReason     失败原因
     * @param submitTime     提交时间
     * @param startTime      开始时间
     * @param finishTime     完成时间
     * @param progress       进度
     * @param properties     公开元信息 JSON
     * @param data           产物 JSON
     * @param privateData    隐私 JSON（含 Key）
     * @param billingContext 计费上下文
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     * @return 重建的任务聚合
     */
    public static Task rehydrate(Long id, String taskId, TaskPlatform platform, Integer userId, String group,
                                 Integer channelId, Long quota, String action, TaskStatus status, String failReason,
                                 Long submitTime, Long startTime, Long finishTime, String progress,
                                 String properties, String data, String privateData, BillingContext billingContext,
                                 Long createdAt, Long updatedAt) {
        // 委托 Builder 装配：20 个字段以具名链式方法对位，避免长位置参数列表（第 11 个 Long 到底是
        // submitTime 还是 startTime，位置参数难辨，Builder 一目了然）。语义与原构造器逐字段一致——
        // 纯还原已存状态，不触发任何业务校验/不变量，statusBeforeAdvance 由构造器同步为 status。
        return builder()
                .id(id)
                .taskId(taskId)
                .platform(platform)
                .userId(userId)
                .group(group)
                .channelId(channelId)
                .quota(quota)
                .action(action)
                .status(status)
                .failReason(failReason)
                .submitTime(submitTime)
                .startTime(startTime)
                .finishTime(finishTime)
                .progress(progress)
                .properties(properties)
                .data(data)
                .privateData(privateData)
                .billingContext(billingContext)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的 20 位长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>触发业务校验与状态机不变量，纯还原已存状态。</p>
     *
     * @return 新的任务重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 任务聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：聚合多数字段为 {@code final}（taskId/platform/userId/group/channelId/quota/action/
     * submitTime/billingContext/createdAt），故 {@link #build()} <b>委托私有全参构造器</b>装配，
     * final 字段在构造器内一次性赋值。与 {@link User} 不同，本聚合字段全为包装类型（{@code Long}/
     * {@code Integer}），无原始类型字段，重建时<b>不做 null→默认归一</b>——保持与原构造器逐字段一致的
     * 还原语义（计费上下文等可空字段允许为 null）。{@code statusBeforeAdvance} 由构造器同步为
     * {@code status}，无需在此重复设置。</p>
     */
    public static final class Builder {
        private Long id;
        private String taskId;
        private TaskPlatform platform;
        private Integer userId;
        private String group;
        private Integer channelId;
        private Long quota;
        private String action;
        private TaskStatus status;
        private String failReason;
        private Long submitTime;
        private Long startTime;
        private Long finishTime;
        private String progress;
        private String properties;
        private String data;
        private String privateData;
        private BillingContext billingContext;
        private Long createdAt;
        private Long updatedAt;

        private Builder() {
        }

        /** @param id 主键（未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param taskId 上游任务 ID */
        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /** @param platform 任务平台 */
        public Builder platform(TaskPlatform platform) {
            this.platform = platform;
            return this;
        }

        /** @param userId 归属用户 id */
        public Builder userId(Integer userId) {
            this.userId = userId;
            return this;
        }

        /** @param group 用户分组（可空） */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /** @param channelId 渠道 id（可空） */
        public Builder channelId(Integer channelId) {
            this.channelId = channelId;
            return this;
        }

        /** @param quota 预扣配额（可空） */
        public Builder quota(Long quota) {
            this.quota = quota;
            return this;
        }

        /** @param action 任务动作 */
        public Builder action(String action) {
            this.action = action;
            return this;
        }

        /** @param status 当前状态 */
        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        /** @param failReason 失败原因（可空） */
        public Builder failReason(String failReason) {
            this.failReason = failReason;
            return this;
        }

        /** @param submitTime 提交时间 epoch 秒（可空） */
        public Builder submitTime(Long submitTime) {
            this.submitTime = submitTime;
            return this;
        }

        /** @param startTime 开始时间 epoch 秒（可空） */
        public Builder startTime(Long startTime) {
            this.startTime = startTime;
            return this;
        }

        /** @param finishTime 完成时间 epoch 秒（可空） */
        public Builder finishTime(Long finishTime) {
            this.finishTime = finishTime;
            return this;
        }

        /** @param progress 进度（如 {@code "0%"}/{@code "100%"}，可空） */
        public Builder progress(String progress) {
            this.progress = progress;
            return this;
        }

        /** @param properties 公开元信息 JSON（可空） */
        public Builder properties(String properties) {
            this.properties = properties;
            return this;
        }

        /** @param data 产物 JSON（可空，已脱敏） */
        public Builder data(String data) {
            this.data = data;
            return this;
        }

        /** @param privateData 隐私 JSON（含 Key，禁下发，可空） */
        public Builder privateData(String privateData) {
            this.privateData = privateData;
            return this;
        }

        /** @param billingContext 计费上下文（退款依据，可空） */
        public Builder billingContext(BillingContext billingContext) {
            this.billingContext = billingContext;
            return this;
        }

        /** @param createdAt 创建时间 epoch 秒（可空） */
        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /** @param updatedAt 更新时间 epoch 秒（可空） */
        public Builder updatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * 装配并返回重建的任务聚合（委托私有全参构造器，不触发业务校验与状态机不变量）。
         *
         * @return 重建的任务聚合
         */
        public Task build() {
            return new Task(id, taskId, platform, userId, group, channelId, quota, action, status, failReason,
                    submitTime, startTime, finishTime, progress, properties, data, privateData, billingContext,
                    createdAt, updatedAt);
        }
    }

    // ---- 行为方法（充血，状态机由聚合守护） ----

    /**
     * 判定 from → to 是否合法转换（PRD AT-1 状态机；终态不可再变）。
     *
     * @param to 目标状态
     * @return 是否合法
     */
    public boolean canTransitionTo(TaskStatus to) {
        if (to == null) {
            return false;
        }
        return LEGAL_TRANSITIONS.getOrDefault(this.status, Set.of()).contains(to);
    }

    /**
     * 推进到中间状态（SUBMITTED/QUEUED/IN_PROGRESS/UNKNOWN，PRD AT-1 轮询推进）。
     *
     * <p>充血：校验状态机合法性后改自身状态、刷新进度/时间、记录 {@link #statusBeforeAdvance}
     * 供仓储 CAS。进入 IN_PROGRESS 首次记 startTime。终态请用 {@link #markSuccess}/{@link #markFailure}。</p>
     *
     * @param to          目标状态（不可为终态——终态走专用方法以触发结算）
     * @param progress    进度（可空保持原值）
     * @param nowEpochSec 当前时间（epoch 秒）
     * @throws InvalidTaskParameterException 状态机非法转换、或试图用本方法进终态时
     */
    public void advanceTo(TaskStatus to, String progress, long nowEpochSec) {
        if (to == null) {
            throw InvalidTaskParameterException.required("target status");
        }
        if (to.isTerminal()) {
            throw new InvalidTaskParameterException(
                    "use markSuccess/markFailure for terminal status, not advanceTo");
        }
        if (!canTransitionTo(to)) {
            throw InvalidTaskParameterException.illegalTransition(this.status.toWire(), to.toWire());
        }
        this.statusBeforeAdvance = this.status;
        // 首次进 IN_PROGRESS 记开始时间（PRD AT-1：startTime 在进入处理态时落）。
        if (to == TaskStatus.IN_PROGRESS && this.startTime == null) {
            this.startTime = nowEpochSec;
        }
        this.status = to;
        if (progress != null) {
            this.progress = progress;
        }
        this.updatedAt = nowEpochSec;
    }

    /**
     * 标记成功终态（PRD AT-1 progress==100% 或上游回报 SUCCESS）。
     *
     * <p>充血：校验可转换→SUCCESS，写 finishTime/progress=100%/产物 data，记录 from 供 CAS。
     * 终态后差额结算经 {@link #settleRefund} 单独触发（应用层在 CAS 成功后调用）。</p>
     *
     * @param resultData  产物 data JSON（可空）
     * @param actualTokens 实际 token 消耗（用于差额结算，可空）
     * @param nowEpochSec 当前时间（epoch 秒）
     * @throws InvalidTaskParameterException 非法转换（如已是终态）
     */
    public void markSuccess(String resultData, Long actualTokens, long nowEpochSec) {
        if (!canTransitionTo(TaskStatus.SUCCESS)) {
            throw InvalidTaskParameterException.illegalTransition(this.status.toWire(), TaskStatus.SUCCESS.toWire());
        }
        this.statusBeforeAdvance = this.status;
        this.status = TaskStatus.SUCCESS;
        this.progress = "100%";
        this.finishTime = nowEpochSec;
        if (resultData != null) {
            this.data = resultData;
        }
        this.updatedAt = nowEpochSec;
        // 注：actualTokens 仅用于 settleRefund 入参重算，不改 billingContext 内字段（不可变 record）。
    }

    /**
     * 标记失败终态（PRD AT-1 错误进 FAILURE，写 failReason，触发全额退款）。
     *
     * @param failReason  失败原因（可空）
     * @param nowEpochSec 当前时间（epoch 秒）
     * @throws InvalidTaskParameterException 非法转换
     */
    public void markFailure(String failReason, long nowEpochSec) {
        if (!canTransitionTo(TaskStatus.FAILURE)) {
            throw InvalidTaskParameterException.illegalTransition(this.status.toWire(), TaskStatus.FAILURE.toWire());
        }
        this.statusBeforeAdvance = this.status;
        this.status = TaskStatus.FAILURE;
        this.finishTime = nowEpochSec;
        this.failReason = failReason;
        this.updatedAt = nowEpochSec;
    }

    /**
     * 判定任务是否超时未完成（PRD AT-3 F-2011 扫描命中条件）。
     *
     * <p>命中条件：{@code progress!=100% AND status NOT IN(FAILURE,SUCCESS) AND submit_time<cutoff}。
     * 命中后由应用层经 CAS 标记超时（转 FAILURE 触发退款），CAS 守卫避免覆盖已自然完成的任务。</p>
     *
     * @param cutoffEpochSec 截止时间（早于此提交且未完成视为超时）
     * @return 是否超时
     */
    public boolean isTimedOut(long cutoffEpochSec) {
        boolean notComplete = !"100%".equals(this.progress);
        boolean notTerminal = !this.status.isTerminal();
        boolean beforeCutoff = this.submitTime != null && this.submitTime < cutoffEpochSec;
        return notComplete && notTerminal && beforeCutoff;
    }

    /**
     * 终态退款/差额结算（PRD AT-4 F-2009 分流树）。
     *
     * <p>领域规则（计费规则见 prd-asynctask AT-4）：
     * <ol>
     *   <li>按次计费（{@code billingContext.perCallBilling=true}）→ {@link RefundResult#SKIP}（跳过差额结算）。</li>
     *   <li>SUCCESS → 按实际 token 重算差额（多退少不补，{@link RefundResult#differential}）。</li>
     *   <li>FAILURE/超时 → 全额退回预扣（{@link RefundResult#fullRefund}）。</li>
     * </ol>
     * 实际退款落账（订阅/钱包）由应用层按 {@link BillingContext#billingSource} 分流执行，并经仓储
     * CAS 守卫写入（PRD AT-4 禁无守卫 bulk update）。本方法只算「退多少」，不直接落账（聚合不跨子域）。</p>
     *
     * @param actualQuota 实际消耗配额（SUCCESS 态按真实 token 折算的额度；FAILURE 态忽略）
     * @return 退款结果（类型 + 退款额度）
     * @throws InvalidTaskParameterException 当任务尚未到终态时（不应结算）
     */
    public RefundResult settleRefund(long actualQuota) {
        if (!this.status.isTerminal()) {
            throw new InvalidTaskParameterException(
                    "cannot settle refund before terminal status, current: " + this.status.toWire());
        }
        if (billingContext == null) {
            // 无计费上下文（历史数据/未计费任务）→ 无退款。
            return RefundResult.SKIP;
        }
        // 规则①：按次计费跳过差额结算（F-2009 PerCallBilling=true）。
        if (billingContext.perCallBilling()) {
            return RefundResult.SKIP;
        }
        long pre = billingContext.preConsumedQuota();
        // 规则②：成功按量任务，按真实消耗重算差额（多退少不补）。
        if (this.status.isSuccess()) {
            return RefundResult.differential(pre, actualQuota);
        }
        // 规则③：失败/超时，全额退回预扣。
        return RefundResult.fullRefund(pre);
    }

    /**
     * 安全产物 URL 访问（PRD AT-4 GetResultURL，仅 SUCCESS 展示）。
     *
     * <p>优先取 {@link #data}（新数据 PrivateData.ResultURL 已由应用层落入 data 的安全部分），
     * 非 SUCCESS 返回 null（不展示）。<b>注意</b>：含 Key 的 privateData 绝不经此暴露——本访问器
     * 只读已脱敏的 data，Key 在 privateData 中由接口层 DTO 裁剪屏蔽。</p>
     *
     * @return 产物 data JSON（仅 SUCCESS 态），否则 null
     */
    public String resultUrl() {
        if (!this.status.isSuccess()) {
            return null;
        }
        return this.data;
    }

    /**
     * 回填数据库生成的主键（save 后调用）。
     *
     * @param assignedId 数据库自增 id
     */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    // ---- 访问器（领域查询，无 setter；状态变更只经行为方法） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 上游任务 ID */
    public String taskId() {
        return taskId;
    }

    /** @return 任务平台 */
    public TaskPlatform platform() {
        return platform;
    }

    /** @return 归属用户 id */
    public Integer userId() {
        return userId;
    }

    /** @return 用户分组（可空） */
    public String group() {
        return group;
    }

    /** @return 渠道 id（可空） */
    public Integer channelId() {
        return channelId;
    }

    /** @return 预扣配额 */
    public Long quota() {
        return quota;
    }

    /** @return 任务动作 */
    public String action() {
        return action;
    }

    /** @return 当前状态 */
    public TaskStatus status() {
        return status;
    }

    /** @return 失败原因（可空） */
    public String failReason() {
        return failReason;
    }

    /** @return 提交时间（epoch 秒） */
    public Long submitTime() {
        return submitTime;
    }

    /** @return 开始时间（epoch 秒，可空） */
    public Long startTime() {
        return startTime;
    }

    /** @return 完成时间（epoch 秒，可空） */
    public Long finishTime() {
        return finishTime;
    }

    /** @return 进度（如 {@code "0%"}/{@code "100%"}） */
    public String progress() {
        return progress;
    }

    /** @return 公开元信息 JSON（可空，客户可见） */
    public String properties() {
        return properties;
    }

    /** @return 产物 data JSON（可空，客户可见，已脱敏） */
    public String data() {
        return data;
    }

    /**
     * @return 隐私 JSON（含上游 Key，<b>禁止下发用户</b>，仅供基础设施持久化/内部流程使用）
     */
    public String privateData() {
        return privateData;
    }

    /** @return 计费上下文（退款依据，可空） */
    public BillingContext billingContext() {
        return billingContext;
    }

    /** @return 创建时间（epoch 秒） */
    public Long createdAt() {
        return createdAt;
    }

    /** @return 更新时间（epoch 秒） */
    public Long updatedAt() {
        return updatedAt;
    }

    /**
     * @return 进入最近一次状态变更前的状态（供仓储 CAS 的 fromStatus WHERE 守卫，PRD AT-1/F-2002）
     */
    public TaskStatus statusBeforeAdvance() {
        return statusBeforeAdvance;
    }
}
