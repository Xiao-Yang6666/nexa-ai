package com.nexa.domain.relay.ir;

/**
 * 流式状态（SSE 回转上下文，保存 message 开闭 / content block index / 累计 usage）。
 *
 * <p>领域规则来源：COMPAT-LAYER-DATA-OBJECTS §4.3 StreamState。把 Anthropic 多 event 与 OpenAI
 * delta+[DONE] 互转时需跟踪：
 * <ul>
 *   <li>messageStarted / messageStopped：是否已发/已收 message_start/stop；</li>
 *   <li>currentContentBlockIndex：当前正在传输的 block index（Anthropic content_block_start index）；</li>
 *   <li>cumulativeUsage：累积 usage（Anthropic 在 message_delta/stop 分段返回 usage）；</li>
 * </ul>
 * 本类 mutable（在同一请求的 chunk 处理流程中持续更新），非线程安全。</p>
 */
public final class StreamState {

    private boolean messageStarted;
    private boolean messageStopped;
    private int currentContentBlockIndex;
    private UsageIR cumulativeUsage = UsageIR.ZERO;
    private String stopReasonWire;

    public boolean isMessageStarted() { return messageStarted; }
    public void markMessageStarted() { this.messageStarted = true; }

    public boolean isMessageStopped() { return messageStopped; }
    public void markMessageStopped() { this.messageStopped = true; }

    public int getCurrentContentBlockIndex() { return currentContentBlockIndex; }
    public void setCurrentContentBlockIndex(int index) { this.currentContentBlockIndex = index; }

    public UsageIR getCumulativeUsage() { return cumulativeUsage; }

    /** 跨 chunk 暂存的停止原因线值（Anthropic message_delta 先到、message_stop 才发终结 IR）。 */
    public String getStopReasonWire() { return stopReasonWire; }
    public void setStopReasonWire(String wire) { this.stopReasonWire = wire; }

    /** 累加 usage（取两者最大值逻辑或直接替换，取决于协议语义；此处简化为替换）。 */
    public void updateUsage(UsageIR usage) {
        if (usage != null) {
            this.cumulativeUsage = usage;
        }
    }
}
