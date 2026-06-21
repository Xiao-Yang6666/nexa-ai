package com.nexa.channel.domain.model;

/**
 * 渠道连通性测试结果值对象（不可变，对齐 openapi ChannelTestResult，F-2017）。
 *
 * <p>承载一次渠道连通性测试的结果：是否成功、耗时 ms、提示/错误信息。由上游探测端口
 * （{@code ChannelProbeClient}）返回，应用层据此回写渠道 test_time/response_time（见
 * {@link Channel#recordTestResult}）。</p>
 *
 * <p>安全：message 为可读提示，不含渠道 key 等凭证（凭证剔除在 infra 实现内）。</p>
 *
 * @param success 是否连通成功
 * @param timeMs  耗时 ms
 * @param message 提示/错误信息（可空）
 */
public record ChannelTestResult(boolean success, double timeMs, String message) {

    /**
     * 成功结果工厂。
     *
     * @param timeMs 耗时 ms
     * @return 成功测试结果
     */
    public static ChannelTestResult success(double timeMs) {
        return new ChannelTestResult(true, timeMs, "");
    }

    /**
     * 失败结果工厂。
     *
     * @param timeMs  耗时 ms
     * @param message 失败原因（无敏感凭证）
     * @return 失败测试结果
     */
    public static ChannelTestResult failure(double timeMs, String message) {
        return new ChannelTestResult(false, timeMs, message);
    }
}
