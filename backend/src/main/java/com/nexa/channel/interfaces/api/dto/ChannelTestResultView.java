package com.nexa.channel.interfaces.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.channel.domain.model.ChannelTestResult;

/**
 * 渠道测试结果视图 DTO（接口层，对齐 openapi ChannelTestResult，F-2017）。
 *
 * <p>安全：message 为可读提示，不含渠道 key 等凭证。</p>
 *
 * @param success 是否连通成功
 * @param time    耗时 ms
 * @param message 提示/错误信息
 */
public record ChannelTestResultView(
        @JsonProperty("success") boolean success,
        @JsonProperty("time") double time,
        @JsonProperty("message") String message) {

    /**
     * 由领域测试结果组装视图。
     *
     * @param r 领域测试结果
     * @return 视图 DTO
     */
    public static ChannelTestResultView from(ChannelTestResult r) {
        return new ChannelTestResultView(r.success(), r.timeMs(), r.message());
    }
}
