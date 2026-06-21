package com.nexa.log.domain.model;

import com.nexa.log.domain.vo.LogType;

/**
 * 日志条目聚合根（日志与用量 BC 的读侧 + 审计写侧充血模型，F-4001~F-4013）。
 *
 * <p><b>双重职责</b>：
 * <ol>
 *   <li><b>读侧</b>：从 logs 表（V11，DB-SCHEMA §5 + 10 新列）重建的全字段领域对象，承载一条调用明细
 *       （三段模型 C→A→B + 协议 + 双价 quota_sell/cost/profit）。可见性裁剪由接口层 DTO 完成
 *       （UserLogView 丢弃 B/成本/利润/渠道；AdminLogView 全量），本聚合持有全字段不做裁剪。</li>
 *   <li><b>审计写侧</b>：通过 {@link #manageAudit}/{@link #securityAudit}/{@link #loginAudit} 工厂
 *       构造审计日志（F-4011 type=3 Manage / F-4012 安全敏感操作 / F-4013 type=7 Login），由其他
 *       bounded context（ops/account）在高危/登录操作后调用本工厂 + 仓储 {@code recordAudit} 落库。</li>
 * </ol>
 * </p>
 *
 * <p>充血模型（backend-engineer §2.2）：日志类型判定（{@link #isConsume()}）、token 合计
 * （{@link #totalTokens()}）等查询行为挂在聚合上，而非散落到 service。零框架依赖（与 LogReadJpaEntity 分离）。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4001~F-4013；DB-SCHEMA §5；COMPAT-LAYER-DATA-OBJECTS §3.1
 * 「客户看不到 B」三道闸之一（序列化层在接口 DTO，本聚合持全字段）。</p>
 */
public class LogEntry {

    private Long id;
    private Long userId;
    private long createdAt;
    private LogType type;
    private String content;
    private String username;
    private String tokenName;
    private String modelName;            // = requestedModel(C)，保留现网报表语义
    private long quota;                  // = quotaSell 口径（实付）
    private int promptTokens;
    private int completionTokens;
    private int useTime;
    private boolean stream;
    private Long channelId;
    private String channelName;
    private Long tokenId;
    private String group;
    private String ip;
    private String requestId;
    private String upstreamRequestId;
    private String other;

    // 三段模型 + 协议 + UA + 双价
    private String requestedModel;       // C（客户可见）
    private String resolvedPublicModel;  // A（客户可见）
    private String actualUpstreamModel;  // B（仅 admin/root）
    private String inboundProtocol;
    private String upstreamProtocol;
    private boolean protocolConverted;
    private String userAgent;
    private int quotaSell;               // 客户可见
    private int quotaCost;               // 仅 admin/root
    private int quotaProfit;             // 仅 admin/root

    private LogEntry() {
    }

    // ===================== 读侧重建 =====================

    /** 仓储重建用的可变 builder（基础设施层从 JpaEntity 填充，避免暴露全参构造）。 */
    public static Builder rebuild() {
        return new Builder();
    }

    /** 读侧重建 builder（仅基础设施仓储调用）。 */
    public static final class Builder {
        private final LogEntry e = new LogEntry();

        public Builder id(Long v) { e.id = v; return this; }
        public Builder userId(Long v) { e.userId = v; return this; }
        public Builder createdAt(long v) { e.createdAt = v; return this; }
        public Builder type(LogType v) { e.type = v; return this; }
        public Builder content(String v) { e.content = v; return this; }
        public Builder username(String v) { e.username = v; return this; }
        public Builder tokenName(String v) { e.tokenName = v; return this; }
        public Builder modelName(String v) { e.modelName = v; return this; }
        public Builder quota(long v) { e.quota = v; return this; }
        public Builder promptTokens(int v) { e.promptTokens = v; return this; }
        public Builder completionTokens(int v) { e.completionTokens = v; return this; }
        public Builder useTime(int v) { e.useTime = v; return this; }
        public Builder stream(boolean v) { e.stream = v; return this; }
        public Builder channelId(Long v) { e.channelId = v; return this; }
        public Builder channelName(String v) { e.channelName = v; return this; }
        public Builder tokenId(Long v) { e.tokenId = v; return this; }
        public Builder group(String v) { e.group = v; return this; }
        public Builder ip(String v) { e.ip = v; return this; }
        public Builder requestId(String v) { e.requestId = v; return this; }
        public Builder upstreamRequestId(String v) { e.upstreamRequestId = v; return this; }
        public Builder other(String v) { e.other = v; return this; }
        public Builder requestedModel(String v) { e.requestedModel = v; return this; }
        public Builder resolvedPublicModel(String v) { e.resolvedPublicModel = v; return this; }
        public Builder actualUpstreamModel(String v) { e.actualUpstreamModel = v; return this; }
        public Builder inboundProtocol(String v) { e.inboundProtocol = v; return this; }
        public Builder upstreamProtocol(String v) { e.upstreamProtocol = v; return this; }
        public Builder protocolConverted(boolean v) { e.protocolConverted = v; return this; }
        public Builder userAgent(String v) { e.userAgent = v; return this; }
        public Builder quotaSell(int v) { e.quotaSell = v; return this; }
        public Builder quotaCost(int v) { e.quotaCost = v; return this; }
        public Builder quotaProfit(int v) { e.quotaProfit = v; return this; }

        /** @return 重建完成的日志聚合 */
        public LogEntry build() { return e; }
    }

    // ===================== 审计写侧工厂 =====================

    /**
     * 构造管理/高危操作审计日志（F-4011，Type=3 Manage）。
     *
     * <p>领域规则来源：prd F-4011「写入记录 Type=3；content 由 action 模板渲染；记操作者与目标」。
     * content 由调用方按 action 模板渲染好传入（如 "Updated system option: RegisterEnabled"），
     * 本工厂只组装聚合不做模板逻辑（模板属调用域职责）。</p>
     *
     * @param operatorUserId 操作者用户 id（管理员）
     * @param operatorName   操作者用户名
     * @param renderedContent action 模板渲染后的可读内容（绝不含敏感 value，F-4018 仅记 key）
     * @param ip             操作来源 IP
     * @param nowEpoch       当前 epoch 秒
     * @return 管理审计日志聚合
     */
    public static LogEntry manageAudit(long operatorUserId, String operatorName,
                                       String renderedContent, String ip, long nowEpoch) {
        LogEntry e = new LogEntry();
        e.type = LogType.MANAGE;
        e.createdAt = nowEpoch;
        e.userId = operatorUserId;
        e.username = operatorName;
        e.content = renderedContent;
        e.ip = ip;
        return e;
    }

    /**
     * 构造用户安全敏感操作审计日志（F-4012，Type=3 Manage，无管理员归属）。
     *
     * <p>领域规则来源：prd F-4012「adminInfo 为 nil；content 渲染为 『Registered a passkey』等」。
     * 与 {@link #manageAudit} 的差异是「无管理员操作者」——操作主体即用户本人（passkey 绑定/解绑等），
     * 故 username 为本人、不记 admin 归属。</p>
     *
     * @param userId          用户本人 id
     * @param username        用户本人用户名
     * @param renderedContent 渲染后的内容（如 "Registered a passkey"）
     * @param ip              来源 IP
     * @param nowEpoch        当前 epoch 秒
     * @return 安全审计日志聚合
     */
    public static LogEntry securityAudit(long userId, String username,
                                         String renderedContent, String ip, long nowEpoch) {
        LogEntry e = new LogEntry();
        e.type = LogType.MANAGE;
        e.createdAt = nowEpoch;
        e.userId = userId;
        e.username = username;
        e.content = renderedContent;
        e.ip = ip;
        return e;
    }

    /**
     * 构造登录审计日志（F-4013，Type=7 Login）。
     *
     * <p>领域规则来源：prd F-4013「写入记录 Type=7；包含登录 username 与 client IP」。</p>
     *
     * @param userId   登录用户 id
     * @param username 登录用户名
     * @param ip       客户端 IP
     * @param content  结构化登录描述（如 "login via password" / "login via github oauth"）
     * @param nowEpoch 当前 epoch 秒
     * @return 登录审计日志聚合
     */
    public static LogEntry loginAudit(long userId, String username, String ip,
                                      String content, long nowEpoch) {
        LogEntry e = new LogEntry();
        e.type = LogType.LOGIN;
        e.createdAt = nowEpoch;
        e.userId = userId;
        e.username = username;
        e.ip = ip;
        e.content = content;
        return e;
    }

    // ===================== 充血查询行为 =====================

    /** @return 是否为消费日志（Type=2，唯一计入统计/排行的类型）。 */
    public boolean isConsume() {
        return type == LogType.CONSUME;
    }

    /** @return prompt + completion token 合计（统计 tpm 用）。 */
    public long totalTokens() {
        return (long) promptTokens + completionTokens;
    }

    // ===================== getters（视图映射用） =====================
    public Long id() { return id; }
    public Long userId() { return userId; }
    public long createdAt() { return createdAt; }
    public LogType type() { return type; }
    public String content() { return content; }
    public String username() { return username; }
    public String tokenName() { return tokenName; }
    public String modelName() { return modelName; }
    public long quota() { return quota; }
    public int promptTokens() { return promptTokens; }
    public int completionTokens() { return completionTokens; }
    public int useTime() { return useTime; }
    public boolean isStream() { return stream; }
    public Long channelId() { return channelId; }
    public String channelName() { return channelName; }
    public Long tokenId() { return tokenId; }
    public String group() { return group; }
    public String ip() { return ip; }
    public String requestId() { return requestId; }
    public String upstreamRequestId() { return upstreamRequestId; }
    public String other() { return other; }
    public String requestedModel() { return requestedModel; }
    public String resolvedPublicModel() { return resolvedPublicModel; }
    public String actualUpstreamModel() { return actualUpstreamModel; }
    public String inboundProtocol() { return inboundProtocol; }
    public String upstreamProtocol() { return upstreamProtocol; }
    public boolean isProtocolConverted() { return protocolConverted; }
    public String userAgent() { return userAgent; }
    public int quotaSell() { return quotaSell; }
    public int quotaCost() { return quotaCost; }
    public int quotaProfit() { return quotaProfit; }
}
