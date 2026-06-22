package com.nexa.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.relay.domain.exception.VideoTaskException;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.service.VideoProxyPolicy;
import com.nexa.relay.domain.vo.VideoTaskStatus;
import com.nexa.task.application.QueryTaskUseCase;
import com.nexa.task.domain.model.Task;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * 视频内容代理用例（RL-5 F-4046，应用层编排）。
 *
 * <p>按 prd-relay RL-5 主流程编排：
 * <ol>
 *   <li><b>vp_own</b>：查 task（self-scope：taskId+userId），不存在/非本人 → 404（不区分，防泄露）；</li>
 *   <li><b>vp_done</b>：{@code Status==SUCCESS} 否则 → 404（含 IN_PROGRESS/FAILURE）；</li>
 *   <li><b>vp_url</b>：从 task.data 解析内容 URL（按渠道 type 可走不同字段，本期取通用 result url 字段）；</li>
 *   <li><b>vp_data</b>：{@code data:} base64 → 解码直出；</li>
 *   <li><b>vp_ssrf</b>：其余 URL 经 {@link VideoProxyPolicy#validateUrl} 校验（内网/回环拒绝）→ 未过 403；</li>
 *   <li><b>vp_copy</b>：流式拉取并回写客户（{@code Cache-Control: max-age=86400}）。</li>
 * </ol>
 * <b>安全</b>：{@code privateData}（含上游 Key）绝不下发——本用例只读 task.data（已脱敏产物），
 * SSRF 防护拒绝内网地址。Key（Gemini x-goog-api-key 等）仅用于拉取时注入鉴权头，不回显。</p>
 */
@Service
public class VideoProxyUseCase {

    /** 客户拿到视频内容的浏览器缓存时长（RL-5 §vp_copy：max-age=86400 = 24h）。 */
    public static final String CACHE_CONTROL = "max-age=86400";

    private final QueryTaskUseCase queryTaskUseCase;
    private final ChannelRepository channelRepo;
    private final UpstreamHttpPort upstreamHttpPort;
    private final ObjectMapper objectMapper;

    public VideoProxyUseCase(QueryTaskUseCase queryTaskUseCase,
                             ChannelRepository channelRepo,
                             UpstreamHttpPort upstreamHttpPort,
                             ObjectMapper objectMapper) {
        this.queryTaskUseCase = queryTaskUseCase;
        this.channelRepo = channelRepo;
        this.upstreamHttpPort = upstreamHttpPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析视频内容来源（vp_own + vp_done + vp_url + vp_ssrf 校验，不含 IO）。
     *
     * <p>校验链全部通过后返回内容来源描述（{@link VideoContent}）：data: 直出字节，或经 SSRF 校验的
     * http(s) URL + 渠道凭证。任一校验失败抛 {@link VideoTaskException}（接口层翻译为 403/404）。</p>
     *
     * @param taskId      视频任务 ID
     * @param actorUserId 当前认证用户 id（self-scope）
     * @return 视频内容来源
     */
    public VideoContent resolveContent(String taskId, int actorUserId) {
        // vp_own：self-scope 查 task，不存在/非本人 → 404（不区分，防任务存在性泄露）。
        Task task = queryTaskUseCase.getByTaskIdForUser(taskId, actorUserId)
                .orElseThrow(() -> VideoTaskException.notFoundOrNotOwner(taskId));

        // vp_done：仅 SUCCESS 终态可取内容。
        VideoTaskStatus status = VideoTaskStatus.fromWire(
                task.status() == null ? null : task.status().toWire());
        if (!status.isSuccess()) {
            throw VideoTaskException.notSuccess(taskId, status.name());
        }

        // vp_url：从 task.data 解析内容 URL。
        String contentUrl = extractContentUrl(task.data());
        if (contentUrl == null || contentUrl.isBlank()) {
            throw VideoTaskException.notSuccess(taskId, "no content url"); // 成功但无产物 URL：按未完成处理。
        }

        // vp_data：data: base64 → 解码直出（不走 SSRF/网络）。
        if (contentUrl.startsWith("data:")) {
            return VideoContent.inline(decodeDataUrl(contentUrl), mediaTypeOfDataUrl(contentUrl));
        }

        // vp_ssrf：内网/回环地址拒绝。
        if (!VideoProxyPolicy.validateUrl(contentUrl)) {
            throw VideoTaskException.ssrfBlocked();
        }

        // 渠道凭证（Gemini/Vertex 等需注入 Key 拉取；OpenAI Sora 公网 URL 无需）。Key 不回显。
        String apiKey = task.channelId() == null ? null
                : channelRepo.findById(task.channelId().longValue())
                        .map(Channel::key).orElse(null);
        return VideoContent.remote(contentUrl, apiKey);
    }

    /**
     * 流式拉取远端视频内容并回写客户 sink（vp_copy，io.Copy 等价）。仅对 {@link VideoContent#isRemote()} 调用。
     *
     * @param content 已校验的远端内容来源
     * @param sink    客户输出流
     */
    public void streamRemote(VideoContent content, OutputStream sink) {
        UpstreamRequest req = UpstreamRequest.of(
                "GET", content.url(), "", content.apiKey(), null, java.util.Map.of());
        upstreamHttpPort.stream(req, new UpstreamHttpPort.UpstreamStreamHandler() {
            @Override
            public void onChunk(byte[] rawChunk) {
                try {
                    sink.write(rawChunk);
                    sink.flush();
                } catch (java.io.IOException e) {
                    throw new VideoTaskException("VIDEO_STREAM_WRITE_FAILED", 502,
                            "failed to write video content to client");
                }
            }

            @Override
            public void onComplete(int statusCode) {
                // 流结束，无需额外处理。
            }

            @Override
            public void onError(int statusCode, byte[] rawBody) {
                throw new VideoTaskException("VIDEO_UPSTREAM_ERROR", 502,
                        "upstream returned error fetching video content: " + statusCode);
            }
        });
    }

    /**
     * 从 task.data JSON 解析视频内容 URL（容错多种字段名约定）。
     *
     * <p>不同上游产物字段名不统一（result_url/video_url/url/content_url），按优先级尝试；data 非 JSON
     * 时按裸 URL 处理（少数上游直接存 URL 字符串）。</p>
     */
    private String extractContentUrl(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            for (String field : new String[]{"result_url", "video_url", "content_url", "url"}) {
                JsonNode n = root.path(field);
                if (n.isTextual() && !n.asText().isBlank()) {
                    return n.asText();
                }
            }
            return null;
        } catch (Exception e) {
            // data 非 JSON：可能是裸 URL 字符串。
            String trimmed = data.trim();
            if (trimmed.startsWith("http") || trimmed.startsWith("data:")) {
                return trimmed;
            }
            return null;
        }
    }

    /** 解码 data: URL 的 base64 部分为字节。 */
    private byte[] decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw VideoTaskException.ssrfBlocked();
        }
        String b64 = dataUrl.substring(comma + 1);
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new VideoTaskException("VIDEO_INVALID_DATA_URL", 400, "invalid data url base64");
        }
    }

    /** 解析 data: URL 的 media type（如 {@code data:video/mp4;base64,...} → video/mp4）。 */
    private String mediaTypeOfDataUrl(String dataUrl) {
        int semi = dataUrl.indexOf(';');
        int colon = dataUrl.indexOf(':');
        if (colon >= 0 && semi > colon) {
            return dataUrl.substring(colon + 1, semi);
        }
        return "application/octet-stream";
    }

    /**
     * 视频内容来源（不可变）：inline（data: 解码字节）或 remote（需流式拉取的 http(s) URL + 凭证）。
     *
     * @param inlineBytes data: 解码后的字节（remote 时为 null）
     * @param mediaType   内容类型（inline 时来自 data: 头）
     * @param url         远端 URL（inline 时为 null）
     * @param apiKey      拉取远端用的渠道凭证（可空，不回显）
     */
    public record VideoContent(byte[] inlineBytes, String mediaType, String url, String apiKey) {

        static VideoContent inline(byte[] bytes, String mediaType) {
            return new VideoContent(bytes, mediaType, null, null);
        }

        static VideoContent remote(String url, String apiKey) {
            return new VideoContent(null, null, url, apiKey);
        }

        public boolean isInline() {
            return inlineBytes != null;
        }

        public boolean isRemote() {
            return url != null;
        }

        public Optional<String> mediaTypeOpt() {
            return Optional.ofNullable(mediaType);
        }
    }
}
