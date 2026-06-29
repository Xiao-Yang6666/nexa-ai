package com.nexa.domain.relay.vo;

/**
 * 单请求中继上下文（运行期，in-memory，RL-7 端到端链路承载字段，衔接 COMPAT-LAYER-DATA-OBJECTS §5）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §5 + prd-relay RL-7。在一笔 relay 请求生命周期内
 * 逐步填充、传递给各步骤（映射→选渠→转换→计费→落 Log）。本类 mutable 由应用层编排使用。</p>
 */
public final class RelayInfo {

    // 三段模型名
    private String requestedModel;       // C
    private String resolvedPublicModel;  // A (= L1后)
    private String upstreamModelName;    // B (= L2后, 选渠用此值查 Ability)

    // 协议
    private ProtocolFormat inboundFormat;
    private ProtocolFormat targetProtocol;
    private boolean passthrough;          // inFmt == target

    // 选中渠道
    private Long channelId;
    private int channelType;

    // 分组/token
    private String usingGroup;
    private Long tokenId;
    private Long userId;
    private String username;
    private String tokenName;

    // 流式
    private boolean stream;

    // 双价记账
    private int quotaSell;
    private int quotaCost;
    private int quotaProfit;

    // 计时
    private long startTimeMs;
    private int useTimeMs;

    // 请求追踪
    private String requestId;
    private String upstreamRequestId;
    private String userAgent;
    private String ip;

    public String requestedModel() { return requestedModel; }
    public void setRequestedModel(String m) { this.requestedModel = m; }

    public String resolvedPublicModel() { return resolvedPublicModel; }
    public void setResolvedPublicModel(String m) { this.resolvedPublicModel = m; }

    public String upstreamModelName() { return upstreamModelName; }
    public void setUpstreamModelName(String m) { this.upstreamModelName = m; }

    public ProtocolFormat inboundFormat() { return inboundFormat; }
    public void setInboundFormat(ProtocolFormat f) { this.inboundFormat = f; }

    public ProtocolFormat targetProtocol() { return targetProtocol; }
    public void setTargetProtocol(ProtocolFormat f) { this.targetProtocol = f; }

    public boolean isPassthrough() { return passthrough; }
    public void setPassthrough(boolean p) { this.passthrough = p; }

    public Long channelId() { return channelId; }
    public void setChannelId(Long id) { this.channelId = id; }

    public int channelType() { return channelType; }
    public void setChannelType(int t) { this.channelType = t; }

    public String usingGroup() { return usingGroup; }
    public void setUsingGroup(String g) { this.usingGroup = g; }

    public Long tokenId() { return tokenId; }
    public void setTokenId(Long id) { this.tokenId = id; }

    public Long userId() { return userId; }
    public void setUserId(Long id) { this.userId = id; }

    public String username() { return username; }
    public void setUsername(String u) { this.username = u; }

    public String tokenName() { return tokenName; }
    public void setTokenName(String n) { this.tokenName = n; }

    public boolean isStream() { return stream; }
    public void setStream(boolean s) { this.stream = s; }

    public int quotaSell() { return quotaSell; }
    public void setQuotaSell(int q) { this.quotaSell = q; }

    public int quotaCost() { return quotaCost; }
    public void setQuotaCost(int q) { this.quotaCost = q; }

    public int quotaProfit() { return quotaProfit; }
    public void setQuotaProfit(int q) { this.quotaProfit = q; }

    public long startTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long t) { this.startTimeMs = t; }

    public int useTimeMs() { return useTimeMs; }
    public void setUseTimeMs(int t) { this.useTimeMs = t; }

    public String requestId() { return requestId; }
    public void setRequestId(String r) { this.requestId = r; }

    public String upstreamRequestId() { return upstreamRequestId; }
    public void setUpstreamRequestId(String r) { this.upstreamRequestId = r; }

    public String userAgent() { return userAgent; }
    public void setUserAgent(String ua) { this.userAgent = ua; }

    public String ip() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    /**
     * 计算 passthrough 标记（RL-6 §3 cp_cmp：inFmt==targetProto → 直通）。
     */
    public void computePassthrough() {
        this.passthrough = (inboundFormat != null && inboundFormat.equals(targetProtocol));
    }
}
