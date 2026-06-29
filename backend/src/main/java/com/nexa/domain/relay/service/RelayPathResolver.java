package com.nexa.domain.relay.service;

import com.nexa.domain.relay.vo.ProtocolFormat;
import com.nexa.domain.relay.vo.RelayDispatch;
import com.nexa.domain.relay.vo.RelayMode;

/**
 * 端点路径 → 中继模式/协议格式 解析器（RL-2 {@code Path2RelayMode}，领域服务，纯函数零框架依赖）。
 *
 * <p>领域规则来源：prd-relay.md RL-2 主流程 rd_p1~rd_p8，**前缀顺序敏感**：
 * <ol>
 *   <li>{@code /v1/responses/compact} 必须先于 {@code /v1/responses}（顺序错会把 compact 误判为普通 responses）；</li>
 *   <li>{@code /v1/messages} → Claude 原生；</li>
 *   <li>前缀 {@code /v1beta/models} → Gemini 原生；</li>
 *   <li>{@code /v1/realtime} → Realtime（WebSocket 升级）；</li>
 *   <li>任意 {@code embeddings} 后缀 → Embedding（不限定完整路径）；</li>
 *   <li>{@code /v1/images/variations} → RelayNotImplemented；</li>
 *   <li>{@code /v1/edits} 独立 legacy（区别于 /images/edits）；</li>
 *   <li>{@code /v1/completions} → legacy completions；</li>
 *   <li>{@code /v1/models} → 模型列表；</li>
 *   <li>其余默认 → chat/completions（OpenAI）。</li>
 * </ol>
 * 匹配按上述顺序自上而下短路。本服务只产出常量，不做转发（衔接 RL-4/RL-6）。</p>
 */
public final class RelayPathResolver {

    private RelayPathResolver() {
    }

    /**
     * 按请求路径解析中继模式 + 入站协议（RL-2 有序前缀匹配）。
     *
     * @param rawPath HTTP 请求路径（如 {@code /v1/chat/completions}）；null/空按默认 chat 处理
     * @return 分发判定（mode + format）
     */
    public static RelayDispatch resolve(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        // 去掉尾随斜杠便于精确匹配，但保留用于 HasSuffix(embeddings) 的判断基准
        String normalized = stripTrailingSlash(path);

        // ① compact 必须先于 responses（顺序铁律，PRD RL-2 §6）
        if (normalized.equals("/v1/responses/compact")) {
            return new RelayDispatch(RelayMode.RESPONSES_COMPACTION, ProtocolFormat.OPENAI_RESPONSES_COMPACTION);
        }
        // ② responses
        if (normalized.equals("/v1/responses")) {
            return new RelayDispatch(RelayMode.RESPONSES, ProtocolFormat.OPENAI_RESPONSES);
        }
        // ③ claude messages
        if (normalized.equals("/v1/messages")) {
            return new RelayDispatch(RelayMode.CLAUDE_MESSAGES, ProtocolFormat.CLAUDE);
        }
        // ④ gemini 前缀
        if (normalized.startsWith("/v1beta/models")) {
            return new RelayDispatch(RelayMode.GEMINI_GENERATE, ProtocolFormat.GEMINI);
        }
        // ⑤ realtime
        if (normalized.equals("/v1/realtime")) {
            return new RelayDispatch(RelayMode.REALTIME, ProtocolFormat.OPENAI_REALTIME);
        }
        // ⑥ embeddings 后缀（不限定完整路径，覆盖 /v1/embeddings 等）
        if (normalized.endsWith("embeddings")) {
            return new RelayDispatch(RelayMode.EMBEDDINGS, ProtocolFormat.EMBEDDING);
        }
        // ⑦ images/variations → 未实现（非 500）
        if (normalized.equals("/v1/images/variations")) {
            return new RelayDispatch(RelayMode.NOT_IMPLEMENTED, ProtocolFormat.OPENAI);
        }
        // ⑧ edits 独立 legacy（区别 /images/edits）
        if (normalized.equals("/v1/edits")) {
            return new RelayDispatch(RelayMode.EDITS, ProtocolFormat.OPENAI);
        }
        // legacy completions
        if (normalized.equals("/v1/completions")) {
            return new RelayDispatch(RelayMode.COMPLETIONS, ProtocolFormat.OPENAI);
        }
        // 模型列表
        if (normalized.equals("/v1/models")) {
            return new RelayDispatch(RelayMode.MODELS_LIST, ProtocolFormat.OPENAI);
        }
        // 默认 chat/completions
        return new RelayDispatch(RelayMode.CHAT_COMPLETIONS, ProtocolFormat.OPENAI);
    }

    private static String stripTrailingSlash(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 解析<b>出站</b>请求路径（RL-6 协议转换缺口补全）：发往上游时，URL path 必须随<b>目标协议</b>走，
     * 而非透传客户入站 path。否则 OpenAI 入站（{@code /v1/chat/completions}）经协议转换发给 Anthropic
     * 上游时，body 已转成 Claude 格式但 path 仍是 OpenAI 端点 → 上游 404。
     *
     * <p>映射规则：
     * <ul>
     *   <li>{@code originalPath} 与目标协议同源（passthrough）→ 原样透传（保留客户私有子路径/查询）；</li>
     *   <li>目标 {@link ProtocolFormat#CLAUDE} → {@code /v1/messages}（Anthropic 原生端点）；</li>
     *   <li>目标 {@link ProtocolFormat#OPENAI} → {@code /v1/chat/completions}（OpenAI chat 端点）；</li>
     *   <li>其余协议（Gemini/Embedding/Responses 等本期未实现转换）→ 原样透传（不阻断）。</li>
     * </ul>
     * 仅覆盖本期已实现协议转换的 chat 主路径；embeddings/responses 等专用端点的出站路径映射待后续补全。</p>
     *
     * @param targetProto  选中账号平台对应的目标协议
     * @param originalPath 客户入站原始路径
     * @param passthrough  入站协议是否等于目标协议（true=同源直转，路径原样透传）
     * @return 发往上游的出站路径
     */
    public static String outboundPath(ProtocolFormat targetProto, String originalPath, boolean passthrough) {
        if (passthrough || targetProto == null) {
            return originalPath;
        }
        return switch (targetProto) {
            case CLAUDE -> "/v1/messages";
            case OPENAI -> "/v1/chat/completions";
            // 其余协议本期无 chat body 转换实现，路径原样透传（回落，不阻断）。
            default -> originalPath;
        };
    }
}
