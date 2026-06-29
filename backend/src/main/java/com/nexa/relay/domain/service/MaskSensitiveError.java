package com.nexa.relay.domain.service;

import java.util.regex.Pattern;

/**
 * 上游错误脱敏领域服务（RL-3 MaskSensitiveErrorWithStatusCode，纯函数零框架依赖）。
 *
 * <p>领域规则来源：prd-relay.md RL-3 + FC-086。上游错误（响应体/异常 message）在落 Log 与回客户前
 * 必须脱敏，<b>绝不泄露上游细节</b>：上游 BaseURL/主机名、Authorization/Bearer token、apiKey、
 * 内网 IP、过长原文等都需剥离或截断。脱敏后产物用于 {@code Log Type=5 Error}.content（re_log）
 * 与按 RelayFormat 构造的对客户错误响应（re_fmt）。</p>
 *
 * <p>策略：先按状态码给一句稳定的、与上游无关的概述（避免把上游错误页/堆栈原样回吐），
 * 再附一段从原始错误里提取并清洗过的简短片段（去敏感词 + 截断），缺失则仅留概述。</p>
 */
public final class MaskSensitiveError {

    /** 脱敏后片段最大长度（防止把上游长错误页/堆栈整段写入 Log 或回客户）。 */
    public static final int MAX_DETAIL_LENGTH = 256;

    /** 敏感片段匹配（凭证/鉴权头/URL/内网主机），命中整体替换为占位。 */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(bearer\\s+[\\w.\\-]+"                 // Bearer token
                    + "|sk-[\\w\\-]+"                    // OpenAI 风格 key
                    + "|authorization\\s*[:=]\\s*\\S+"   // Authorization 头
                    + "|api[_-]?key\\s*[:=]\\s*\\S+"     // apiKey 字段
                    + "|https?://\\S+"                    // 上游 URL（含主机名/路径）
                    + ")");

    private MaskSensitiveError() {
    }

    /**
     * 脱敏上游错误（RL-3 MaskSensitiveErrorWithStatusCode）。
     *
     * @param statusCode 上游 HTTP 状态码
     * @param rawDetail  上游原始错误片段（响应体文本 / 异常 message，可空）
     * @return 脱敏且截断后的安全错误描述（绝不含上游凭证/URL/主机名）
     */
    public static String mask(int statusCode, String rawDetail) {
        String summary = summarize(statusCode);
        if (rawDetail == null || rawDetail.isBlank()) {
            return summary;
        }
        String cleaned = SENSITIVE.matcher(rawDetail).replaceAll("[redacted]")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return summary;
        }
        if (cleaned.length() > MAX_DETAIL_LENGTH) {
            cleaned = cleaned.substring(0, MAX_DETAIL_LENGTH);
        }
        return summary + ": " + cleaned;
    }

    /** 按状态码给与上游无关的稳定概述（不泄露上游身份）。 */
    private static String summarize(int statusCode) {
        if (statusCode == 429) {
            return "upstream rate limited (429)";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "upstream authentication failed (" + statusCode + ")";
        }
        if (statusCode >= 500) {
            return "upstream server error (" + statusCode + ")";
        }
        if (statusCode >= 400) {
            return "upstream request rejected (" + statusCode + ")";
        }
        return "upstream error (" + statusCode + ")";
    }
}
