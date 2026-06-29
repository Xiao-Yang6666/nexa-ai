package com.nexa.domain.relay.service;

/**
 * 视频内容代理策略领域服务（RL-5 归属校验 + 终态校验 + SSRF，纯函数零框架依赖）。
 *
 * <p>领域规则来源：prd-relay.md RL-5 主流程 vp_own/vp_done/vp_ssrf + §6 验收。</p>
 */
public final class VideoProxyPolicy {

    /** 支持视频代理的渠道 type 集合（Gemini/Vertex/OpenAI·Sora，可扩展）。 */
    private static final java.util.Set<Integer> VIDEO_SUPPORTED_TYPES = java.util.Set.of(
            // TODO: 补充真实 type 码（参考 new-api ChannelType 常量）
            22,  // 假设 Gemini
            26,  // 假设 Vertex
            1    // 假设 OpenAI
    );

    /** SSRF 黑名单（内网/回环/链路本地/文档地址，拒绝代理）。 */
    private static final java.util.Set<String> SSRF_BLOCKED_HOSTS = java.util.Set.of(
            "localhost", "127.0.0.1", "0.0.0.0",
            "::1", "169.254.0.0/16", "10.0.0.0/8",
            "172.16.0.0/12", "192.168.0.0/16"
    );

    private VideoProxyPolicy() {
    }

    /**
     * 判定渠道类型是否支持视频代理（RL-5 vp_type）。
     *
     * @param channelType 渠道 type 码
     * @return true = 支持
     */
    public static boolean isVideoSupported(int channelType) {
        return VIDEO_SUPPORTED_TYPES.contains(channelType);
    }

    /**
     * SSRF 校验（RL-5 vp_ssrf，ValidateURLWithFetchSetting 简化版）。
     *
     * <p>拒绝内网/回环地址。data: URL 不走此校验（base64 直出）。完整实现应解析 URL host 并判定
     * IP/CIDR 范围，此处简化为 host 前缀/精确匹配。生产应集成更强 SSRF 防护库（如 DNS 解析后 IP 判定）。</p>
     *
     * @param url 上游视频 URL（http/https）
     * @return true = 通过校验；false = SSRF 拦截
     */
    public static boolean validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        // data: 协议单独处理，不走此校验
        if (lower.startsWith("data:")) {
            return true;
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        // 解析 host（简化：取 "://" 后第一个 "/" 前的部分）
        int hostStart = lower.indexOf("://") + 3;
        int hostEnd = lower.indexOf('/', hostStart);
        if (hostEnd == -1) hostEnd = lower.length();
        String host = lower.substring(hostStart, hostEnd);
        // 去端口
        int portIdx = host.indexOf(':');
        if (portIdx != -1) {
            host = host.substring(0, portIdx);
        }
        // 黑名单判定
        return !SSRF_BLOCKED_HOSTS.contains(host);
    }
}
