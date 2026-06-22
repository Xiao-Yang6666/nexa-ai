package com.nexa.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.billing.application.port.UserQuotaAccount;
import com.nexa.billing.domain.vo.Quota;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.model.ChannelModelCost;
import com.nexa.channel.domain.repository.ChannelModelCostRepository;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.model.domain.model.PublicModel;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.relay.domain.exception.InvalidRelayParameterException;
import com.nexa.relay.domain.exception.NoAvailableChannelException;
import com.nexa.relay.domain.exception.UpstreamException;
import com.nexa.relay.domain.ir.ChatIR;
import com.nexa.relay.domain.ir.ChatRespIR;
import com.nexa.relay.domain.ir.UsageIR;
import com.nexa.relay.domain.model.RelayLog;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.port.UpstreamResponse;
import com.nexa.relay.domain.protocol.ProtocolAdapter;
import com.nexa.relay.domain.protocol.ProtocolRegistry;
import com.nexa.relay.domain.repository.PlatformModelMappingRepository;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.service.DualPriceBilling;
import com.nexa.relay.domain.service.MaskSensitiveError;
import com.nexa.relay.domain.service.RelayPathResolver;
import com.nexa.relay.domain.service.RetryPolicy;
import com.nexa.relay.domain.service.TwoLayerModelResolver;
import com.nexa.relay.domain.vo.AliasScope;
import com.nexa.relay.domain.vo.BillingResult;
import com.nexa.relay.domain.vo.ChannelProtocolMapping;
import com.nexa.routing.application.SelectRelayChannelUseCase;
import com.nexa.relay.domain.vo.ModelResolution;
import com.nexa.relay.domain.vo.ProtocolFormat;
import com.nexa.relay.domain.vo.RelayDispatch;
import com.nexa.relay.domain.vo.RelayInfo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Relay 中继转发用例（RL-1/RL-7 端到端编排：协议识别→两层映射→选渠→协议转换→调上游→计费→落 Log）。
 *
 * <p>应用层编排，薄壳无业务规则——业务全在 domain 层（映射/转换/计费/重试由各 domain service 完成）。
 * 本用例是 F-3026/F-3035/F-3060/F-3061 的入口。完整 HTTP client 转发需与 channel/routing/billing BC 集成，
 * 本期搭建骨架确保编译通过，后续 wave 注入跨 BC 端口与 HTTP client。</p>
 */
@Service
public class RelayForwardUseCase {

    private final PlatformModelMappingRepository l2Repo;
    private final UserModelAliasRepository l1Repo;
    private final RelayLogRepository logRepo;
    private final UpstreamHttpPort upstreamHttpPort;
    private final ChannelRepository channelRepo;
    private final ObjectMapper objectMapper;
    private final KeyLimitGuard keyLimitGuard;
    private final SelectRelayChannelUseCase selectRelayChannelUseCase;
    private final PublicModelRepository publicModelRepo;
    private final ChannelModelCostRepository channelModelCostRepo;
    private final UserQuotaAccount userQuotaAccount;

    public RelayForwardUseCase(PlatformModelMappingRepository l2Repo,
                               UserModelAliasRepository l1Repo,
                               RelayLogRepository logRepo,
                               UpstreamHttpPort upstreamHttpPort,
                               ChannelRepository channelRepo,
                               ObjectMapper objectMapper,
                               KeyLimitGuard keyLimitGuard,
                               SelectRelayChannelUseCase selectRelayChannelUseCase,
                               PublicModelRepository publicModelRepo,
                               ChannelModelCostRepository channelModelCostRepo,
                               UserQuotaAccount userQuotaAccount) {
        this.l2Repo = l2Repo;
        this.l1Repo = l1Repo;
        this.logRepo = logRepo;
        this.upstreamHttpPort = upstreamHttpPort;
        this.channelRepo = channelRepo;
        this.objectMapper = objectMapper;
        this.keyLimitGuard = keyLimitGuard;
        this.selectRelayChannelUseCase = selectRelayChannelUseCase;
        this.publicModelRepo = publicModelRepo;
        this.channelModelCostRepo = channelModelCostRepo;
        this.userQuotaAccount = userQuotaAccount;
    }

    /**
     * 解析请求路径分发（RL-2）。
     *
     * @param path HTTP 请求路径
     * @return 分发结果（mode + format）
     */
    public RelayDispatch resolveDispatch(String path) {
        return RelayPathResolver.resolve(path);
    }

    /**
     * 执行两层模型映射 C→A→B（RL-7 第②步）。
     *
     * @param requestedModel 客户输入名 C
     * @param userId         当前 userId（L1 user scope）
     * @param group          当前分组（L1 group scope）
     * @return 三段映射结果
     */
    public ModelResolution resolveModel(String requestedModel, long userId, String group) {
        AliasScope userScope = AliasScope.user(userId);
        AliasScope groupScope = AliasScope.group(group);
        return TwoLayerModelResolver.resolve(
                requestedModel,
                // L1 lookup: user > group 优先级
                alias -> l1Repo.findTargetByAlias(userScope, alias)
                        .orElseGet(() -> l1Repo.findTargetByAlias(groupScope, alias).orElse(null)),
                // L2 lookup
                publicName -> l2Repo.findUpstreamByPublicName(publicName).orElse(null)
        );
    }

    /**
     * 转发主干编排（RL-7 端到端最小可用路径：OpenAI 非流式、单渠道、不重试）。
     *
     * <p>按 prd-relay RL-7 顺序串起：① 协议识别 → ② C→A→B 两层映射 → ③ key 减法校验（REQ-06）
     * → ④ 选渠（REQ-03）→ ⑤ 协议转换 + 调上游（REQ-04/REQ-01）→ ⑥⑦ 双价记账（REQ-05）
     * → ⑨ 落 Log。本期仅接通最小骨架：③⑥⑦ 留 TODO 占位，④ 用最简「按 group×B 匹配首个可用渠道」
     * 占位（REQ-03 接入完整加权随机/亲和/重试后替换），⑤ 仅 OpenAI 非流式 passthrough（仅改写 body 的
     * model 字段为真实上游名 B，透传字节），⑨ 用零计费 + 上游 usage 落一条消费 Log。</p>
     *
     * <p>不吞错：请求体解析失败抛 {@link InvalidRelayParameterException}；无可用渠道抛
     * {@link NoAvailableChannelException}；上游网络层失败由端口抛 {@link UpstreamException}。
     * 全部由接口层 {@code RelayExceptionHandler} 翻译为 HTTP 错误信封。</p>
     *
     * @param path        HTTP 请求路径（RL-2 分发用）
     * @param body        客户原始请求体字节（含 model 字段）
     * @param authContext 已认证调用方上下文（userId/group/token）
     * @return 转发结果（透传上游 status + headers + body）
     */
    public RelayForwardResult forward(String path, byte[] body, RelayAuthContext authContext) {
        // ① 协议识别（RL-2）。
        RelayDispatch dispatch = resolveDispatch(path);
        // ② 解析请求体取 model 名（C），执行两层映射 C→A→B（RL-7 第②步）。
        JsonNode root = parseBody(body);
        String requestedModel = readModelName(root);
        ModelResolution resolution =
                resolveModel(requestedModel, authContext.userId(), authContext.group());
        String upstreamModel = resolution.upstream(); // B：真实上游模型名

        // ③ key 减法校验（ModelLimits 对 A / EndpointLimits 对 inFmt，默认全开放行）。
        // REQ-06: 接入 KeyLimitGuard（tokenId 为空时——token 鉴权未接线——按默认全开放行）。
        if (authContext.tokenId() != null) {
            keyLimitGuard.check(authContext.tokenId(), resolution.resolvedPublic(), dispatch.format());
        }

        // ④⑤ 选渠 + 协议转换 + 调上游，按 RL-3 状态码重试/禁用包进重试循环（REQ-09）。
        // 计费⑥⑦/结算⑦'/消费 Log⑨ 仅在最终成功的渠道上执行（循环外成功路径），
        // 错误 Log（Type=5）每次失败都记，AutoBan 命中即禁用——不破坏既有成功路径语义。
        // REQ-04: 头尾决策在选渠后做——targetProto 取决于选中渠道的 type，故 body 转换在循环内逐渠道进行：
        //   inFmt == protocolOf(channel.type) → passthrough（仅改写 model 为 B 透传）；
        //   不等 → ProtocolRegistry.get(inFmt).parseRequest → IR(model=B) → targetAdapter.serializeRequest。
        ProtocolFormat inFmt = dispatch.format();
        java.util.Set<Long> triedChannelIds = new java.util.HashSet<>();
        int retryCount = 0;
        int lastFailureStatus = 502;
        String lastFailureMaskedMessage = MaskSensitiveError.mask(lastFailureStatus, null);
        Channel channel;
        ProtocolFormat targetProto;
        boolean passthrough;
        UpstreamResponse upstreamResponse;

        while (true) {
            // ④ 选渠（group×B 加权随机 + CH-5 排除已尝试渠道再选下一个，REQ-03 重载）。
            //   首次选渠无候选 → 上抛 NoAvailableChannelException（503，对齐 RL-1 §4）。
            //   重试中无更多候选（triedChannelIds 非空后耗尽）→ 终止重试，返回上游最后一次错误。
            try {
                channel = selectRelayChannelUseCase.selectChannel(
                        authContext.group(), upstreamModel, triedChannelIds);
            } catch (NoAvailableChannelException e) {
                if (triedChannelIds.isEmpty()) {
                    throw e; // 首次即无可用渠道：保持原 503 语义。
                }
                // 已尝试过渠道但候选耗尽（CH-5 全组耗尽）：返回最后一次上游错误（重试耗尽态）。
                return buildErrorResult(dispatch, lastFailureStatus, lastFailureMaskedMessage);
            }

            // ⑤ RL-6 头尾决策 + 协议转换（REQ-04）：按选中渠道目标协议构造出站 body。
            targetProto = ChannelProtocolMapping.protocolOf(channel.type().code());
            passthrough = inFmt == targetProto;
            byte[] upstreamBody = buildUpstreamBody(root, inFmt, targetProto, upstreamModel, passthrough);

            UpstreamRequest upstreamRequest = UpstreamRequest.of(
                    "POST", channel.baseUrl(), path, channel.key(), upstreamBody,
                    java.util.Map.of("Content-Type", "application/json"));
            int failureStatus;
            String failureDetail;
            try {
                upstreamResponse = upstreamHttpPort.send(upstreamRequest);
                if (upstreamResponse.isSuccessful()) {
                    break; // 2xx：跳出循环，按最终成功渠道走计费/落消费 Log。
                }
                failureStatus = upstreamResponse.statusCode();
                failureDetail = extractErrorDetail(upstreamResponse);
            } catch (UpstreamException e) {
                // 上游传输层失败（连接/超时等）：纳入同一 RL-3 处置链，按其状态码驱动重试判定。
                failureStatus = e.upstreamStatusCode();
                failureDetail = e.getMessage();
            }

            // re_log + re_ban：脱敏落错误 Log（Type=5）+ AutoBan 命中则禁用渠道（CH-3）。
            String masked = handleUpstreamFailure(
                    dispatch, resolution, channel, authContext, failureStatus, failureDetail);
            triedChannelIds.add(channel.id());
            lastFailureStatus = failureStatus;
            lastFailureMaskedMessage = masked;

            // re_retry + re_count：可重试码且重试次数 < RetryTimes → 换渠道重试；否则终止返错。
            if (RetryPolicy.shouldRetry(failureStatus) && retryCount < maxRetryTimes()) {
                retryCount++;
                continue;
            }
            return buildErrorResult(dispatch, failureStatus, masked);
        }

        // ⑧ 响应回转（RL-6）：passthrough 直通 / 否则 targetAdapter.parseResponse → IR → inAdapter.serializeResponse。
        //   流式（client stream:true）回转见 REQ-08 流式分支，本期非流式经 convertResponseBody。
        byte[] clientBody = convertResponseBody(
                upstreamResponse.body(), inFmt, targetProto, passthrough);

        // ⑥⑦ 双价记账（quota_sell/quota_cost/quota_profit，REQ-05）。
        // 第15步：从上游响应体（targetProto 口径）解析真实 usage（D5 prompt/completion tokens）。
        UsageIR usage = parseUsage(upstreamResponse, targetProto);
        // 第16步售价 / 第17-18步成本：调 DualPriceBilling 纯函数算三金额。
        //   售价 quota_sell = BasePriceRatio(A) × GroupRatio(group) × tokens（用公开名 A + 分组折扣，恒定，不随渠道变）。
        //   成本 quota_cost = CostRatio(实际 ChannelId, B) × tokens（用真实渠道 id + 上游名 B，不乘 GroupRatio，ADR-BILL-02）。
        //   成本行 (ChannelId,B) 缺失 → quota_cost=0、quota_profit=quota_sell、costMissing=true（不阻断，落 Log Other 写 cost_missing）。
        BigDecimal basePriceRatio = resolveBasePriceRatio(resolution.resolvedPublic());
        BigDecimal groupRatio = resolveGroupRatio(authContext.group());
        BigDecimal costRatio = resolveCostRatio(channel.id(), upstreamModel); // null = 成本行缺失
        BillingResult billing = DualPriceBilling.compute(
                usage, basePriceRatio, groupRatio, costRatio, BigDecimal.ONE);

        // ⑦' 结算扣减（响应后一次性扣售价，最小闭环）。
        // TODO REQ-05 完整：补「选渠后预扣 + 响应后多退少补」分段结算（§6 第8-9/19步），本期仅响应后一次性扣 quota_sell。
        settle(authContext.userId(), billing);

        // ⑨ 落 Log（RL-7 第⑨步，Type=2 Consume）：含 C/A/B 三段 + 协议三字段（inFmt/targetProto/converted）+ channel_id + 三金额。
        recordConsumeLog(dispatch, resolution, channel, targetProto, authContext, usage, billing);

        // 透传上游 status + headers + 回转后 body（非流式）。
        return new RelayForwardResult(
                upstreamResponse.statusCode(), upstreamResponse.headers(), clientBody);
    }

    /** 客户是否请求流式（body.stream==true，RL-8）。控制器据此决定走 SSE 流式回写还是非流式。 */
    public boolean wantsStream(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("stream").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 流式转发主干（REQ-08，RL-8 SSE 1→N）：与 {@link #forward} 同样的鉴权→映射→key校验→选渠→头尾决策，
     * 但出站请求 {@code stream=true}，上游 SSE 逐 chunk 经 {@code parseStreamChunk → IR → serializeStreamChunk}
     * 转回客户协议并即时 flush；流末按累计 usage 走双价记账落 Log（REQ-05）。
     *
     * <p>渠道不在 streamSupportedChannels（本期简化：所有渠道按支持处理）时可降级非流式——降级策略待
     * REQ-08 增强；当前若上游不支持 SSE，{@code parseStreamChunk} 解析不出事件则原样回写（不阻断）。</p>
     *
     * @param path        请求路径
     * @param body        客户原始请求体（stream=true）
     * @param authContext 鉴权上下文
     * @param sink        客户输出流（SSE 写入目标，由控制器 StreamingResponseBody 提供）
     */
    public void forwardStream(String path, byte[] body, RelayAuthContext authContext, java.io.OutputStream sink) {
        RelayDispatch dispatch = resolveDispatch(path);
        JsonNode root = parseBody(body);
        String requestedModel = readModelName(root);
        ModelResolution resolution =
                resolveModel(requestedModel, authContext.userId(), authContext.group());
        String upstreamModel = resolution.upstream();
        if (authContext.tokenId() != null) {
            keyLimitGuard.check(authContext.tokenId(), resolution.resolvedPublic(), dispatch.format());
        }

        ProtocolFormat inFmt = dispatch.format();
        java.util.Set<Long> triedChannelIds = new java.util.HashSet<>();
        int retryCount = 0;

        while (true) {
            Channel channel;
            try {
                channel = selectRelayChannelUseCase.selectChannel(
                        authContext.group(), upstreamModel, triedChannelIds);
            } catch (NoAvailableChannelException e) {
                if (triedChannelIds.isEmpty()) {
                    throw e;
                }
                return; // 重试耗尽：流已可能部分写出，静默结束（错误 Log 已逐次记录）。
            }

            ProtocolFormat targetProto = ChannelProtocolMapping.protocolOf(channel.type().code());
            boolean passthrough = inFmt == targetProto;
            byte[] upstreamBody = buildUpstreamBody(root, inFmt, targetProto, upstreamModel, passthrough);

            UpstreamRequest upstreamRequest = UpstreamRequest.of(
                    "POST", channel.baseUrl(), path, channel.key(), upstreamBody,
                    java.util.Map.of("Content-Type", "application/json"));

            StreamConversionHandler handler = new StreamConversionHandler(
                    sink, inFmt, targetProto, passthrough);
            try {
                upstreamHttpPort.stream(upstreamRequest, handler);
            } catch (UpstreamException e) {
                handleUpstreamFailure(dispatch, resolution, channel, authContext,
                        e.upstreamStatusCode(), e.getMessage());
                triedChannelIds.add(channel.id());
                if (RetryPolicy.shouldRetry(e.upstreamStatusCode()) && retryCount < maxRetryTimes()
                        && !handler.hasWritten()) {
                    retryCount++;
                    continue;
                }
                return;
            }

            // 上游开流前即报错（HTTP 非 2xx）：按 RL-3 重试/禁用，未写出任何 chunk 才可换渠道重试。
            if (handler.errorStatus() != null) {
                int failureStatus = handler.errorStatus();
                handleUpstreamFailure(dispatch, resolution, channel, authContext,
                        failureStatus, handler.errorDetail());
                triedChannelIds.add(channel.id());
                if (RetryPolicy.shouldRetry(failureStatus) && retryCount < maxRetryTimes()
                        && !handler.hasWritten()) {
                    retryCount++;
                    continue;
                }
                // 不可重试：把脱敏错误作为 SSE 事件写给客户后结束。
                writeStreamError(sink, dispatch, failureStatus,
                        MaskSensitiveError.mask(failureStatus, handler.errorDetail()));
                return;
            }

            // 成功：流已逐 chunk 写出，按累计 usage 走双价记账落 Log（REQ-05）。
            UsageIR usage = handler.cumulativeUsage();
            BigDecimal basePriceRatio = resolveBasePriceRatio(resolution.resolvedPublic());
            BigDecimal groupRatio = resolveGroupRatio(authContext.group());
            BigDecimal costRatio = resolveCostRatio(channel.id(), upstreamModel);
            BillingResult billing = DualPriceBilling.compute(
                    usage, basePriceRatio, groupRatio, costRatio, BigDecimal.ONE);
            settle(authContext.userId(), billing);
            recordConsumeLog(dispatch, resolution, channel, targetProto, authContext, usage, billing);
            return;
        }
    }

    /**
     * 流式转换回调（REQ-08）：上游 SSE 原始块 → {@code parseStreamChunk → IR delta → serializeStreamChunk}
     * （inFmt 协议）→ 写客户 sink。passthrough 时原样转发原始块。累计 usage 供流末计费。
     */
    private final class StreamConversionHandler implements UpstreamHttpPort.UpstreamStreamHandler {
        private final java.io.OutputStream sink;
        private final ProtocolFormat inFmt;
        private final ProtocolFormat targetProto;
        private final boolean passthrough;
        private final com.nexa.relay.domain.ir.StreamState upstreamState = new com.nexa.relay.domain.ir.StreamState();
        private final com.nexa.relay.domain.ir.StreamState clientState = new com.nexa.relay.domain.ir.StreamState();
        private boolean written;
        private Integer errorStatus;
        private String errorDetail;

        StreamConversionHandler(java.io.OutputStream sink, ProtocolFormat inFmt,
                                ProtocolFormat targetProto, boolean passthrough) {
            this.sink = sink;
            this.inFmt = inFmt;
            this.targetProto = targetProto;
            this.passthrough = passthrough;
        }

        @Override
        public void onChunk(byte[] rawChunk) {
            try {
                if (passthrough) {
                    // 同协议直转，但仍解析以累计 usage 供计费（OpenAI/Claude 均可）。
                    ProtocolAdapter target = ProtocolRegistry.get(targetProto).orElse(null);
                    if (target != null) {
                        target.parseStreamChunk(rawChunk, upstreamState); // 仅为累计 usage 副作用
                    }
                    sink.write(rawChunk);
                    sink.flush();
                    written = true;
                    return;
                }
                ProtocolAdapter targetAdapter = ProtocolRegistry.get(targetProto).orElse(null);
                ProtocolAdapter inAdapter = ProtocolRegistry.get(inFmt).orElse(null);
                if (targetAdapter == null || inAdapter == null) {
                    sink.write(rawChunk); // 回落直转。
                    sink.flush();
                    written = true;
                    return;
                }
                for (com.nexa.relay.domain.ir.ChatDeltaIR delta
                        : targetAdapter.parseStreamChunk(rawChunk, upstreamState)) {
                    for (byte[] outEvent : inAdapter.serializeStreamChunk(delta, clientState)) {
                        sink.write(outEvent);
                    }
                }
                sink.flush();
                written = true;
            } catch (java.io.IOException e) {
                throw new UpstreamException(499, "client stream write failed: " + e.getClass().getSimpleName());
            }
        }

        @Override
        public void onComplete(int statusCode) {
            // 流正常结束：usage 已在 onChunk 累计于 upstreamState。
        }

        @Override
        public void onError(int statusCode, byte[] rawBody) {
            this.errorStatus = statusCode;
            this.errorDetail = rawBody == null ? null
                    : new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        }

        boolean hasWritten() { return written; }
        Integer errorStatus() { return errorStatus; }
        String errorDetail() { return errorDetail; }
        UsageIR cumulativeUsage() { return upstreamState.getCumulativeUsage(); }
    }

    /** 流式路径上游报错且不可重试时，把脱敏错误作为一个 SSE 事件写给客户后结束流。 */
    private void writeStreamError(java.io.OutputStream sink, RelayDispatch dispatch,
                                  int statusCode, String maskedMessage) {
        try {
            RelayForwardResult err = buildErrorResult(dispatch, statusCode, maskedMessage);
            sink.write(("data: " + new String(err.body(), java.nio.charset.StandardCharsets.UTF_8) + "\n\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sink.write("data: [DONE]\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sink.flush();
        } catch (java.io.IOException ignored) {
            // 客户已断开：无法回写，静默结束。
        }
    }

    /** 解析客户请求体为 JSON 树（非 JSON / 空体即非法入参，不吞错）。 */
    private JsonNode parseBody(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InvalidRelayParameterException("request body must not be empty");
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new InvalidRelayParameterException("request body is not valid JSON");
        }
    }

    /** 读取请求体 model 字段（C，缺失/空白即非法入参）。 */
    private String readModelName(JsonNode root) {
        JsonNode modelNode = root.get("model");
        if (modelNode == null || !modelNode.isTextual() || modelNode.asText().isBlank()) {
            throw new InvalidRelayParameterException("request body must contain a non-blank 'model'");
        }
        return modelNode.asText();
    }

    /**
     * RL-6 头尾决策构造出站请求体（REQ-04）。
     *
     * <p>passthrough（inFmt==targetProto）：仅改写 {@code model} 字段为 B 后透传原 body（保留客户私有参数）；
     * 否则经 IR 转换：{@code inAdapter.parseRequest(原body) → IR(model 改为 B) → targetAdapter.serializeRequest}。
     * 任一协议未注册（注册表未命中）→ 回落 passthrough 直改 model（RL-6 cp_legacy，不阻断现网）。</p>
     */
    private byte[] buildUpstreamBody(JsonNode root, ProtocolFormat inFmt, ProtocolFormat targetProto,
                                     String upstreamModel, boolean passthrough) {
        if (passthrough) {
            return rewriteModel(root, upstreamModel);
        }
        ProtocolAdapter inAdapter = ProtocolRegistry.get(inFmt).orElse(null);
        ProtocolAdapter targetAdapter = ProtocolRegistry.get(targetProto).orElse(null);
        if (inAdapter == null || targetAdapter == null) {
            // 注册表未命中（如 Gemini 预留位）：回落直改 model 透传，不阻断（RL-6 cp_legacy）。
            return rewriteModel(root, upstreamModel);
        }
        byte[] rawIn = toBytes(root);
        ChatIR ir = inAdapter.parseRequest(rawIn);
        // model 恒改为真实上游名 B（两层映射产出），其余 IR 字段由 inAdapter 已归一。
        ChatIR irForUpstream = ir.model().equals(upstreamModel) ? ir : withModel(ir, upstreamModel);
        return targetAdapter.serializeRequest(irForUpstream);
    }

    /**
     * RL-6 响应回转（REQ-04 第⑧步）。
     *
     * <p>passthrough：上游响应体即客户入站协议，直通；否则经 IR 反向：
     * {@code targetAdapter.parseResponse(上游body) → IR → inAdapter.serializeResponse} 还原成客户协议。
     * 协议未注册回落直通。空 body 原样返回。</p>
     */
    private byte[] convertResponseBody(byte[] upstreamBody, ProtocolFormat inFmt,
                                       ProtocolFormat targetProto, boolean passthrough) {
        if (passthrough || upstreamBody == null || upstreamBody.length == 0) {
            return upstreamBody;
        }
        ProtocolAdapter inAdapter = ProtocolRegistry.get(inFmt).orElse(null);
        ProtocolAdapter targetAdapter = ProtocolRegistry.get(targetProto).orElse(null);
        if (inAdapter == null || targetAdapter == null) {
            return upstreamBody; // 回落直通。
        }
        ChatRespIR ir = targetAdapter.parseResponse(upstreamBody);
        return inAdapter.serializeResponse(ir);
    }

    /** ChatIR 改 model（IR 为不可变 record，用 Builder 重建并复制全字段）。 */
    private ChatIR withModel(ChatIR ir, String model) {
        ChatIR.Builder b = ChatIR.builder(model)
                .system(ir.system())
                .messages(ir.messages())
                .tools(ir.tools())
                .toolChoice(ir.toolChoice())
                .stream(ir.stream())
                .maxTokens(ir.maxTokens())
                .temperature(ir.temperature())
                .topP(ir.topP())
                .stopSequences(ir.stopSequences())
                .passthrough(ir.passthroughExtras());
        if (ir.metadata() != null) {
            b.metadata(ir.metadata());
        }
        return b.build();
    }

    /** JsonNode → 字节（IR 转换入口需原始 body 字节）。 */
    private byte[] toBytes(JsonNode root) {
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new InvalidRelayParameterException("failed to serialize request body");
        }
    }

    /** 改写请求体 model 字段为真实上游名 B（passthrough，其余字段透传）。 */
    private byte[] rewriteModel(JsonNode root, String upstreamModel) {
        try {
            ObjectNode rewritten = ((ObjectNode) root).deepCopy();
            rewritten.put("model", upstreamModel);
            return objectMapper.writeValueAsBytes(rewritten);
        } catch (IOException e) {
            throw new InvalidRelayParameterException("failed to rewrite request body model");
        }
    }

    /**
     * 选渠占位（REQ-03 接线点）：按 group×B 匹配首个启用且声明该上游模型 B、且配置了 BaseURL 的渠道。
     *
     * <p>仅满足「OpenAI 非流式、单渠道、不重试」最小路径；加权随机、CH-4 亲和、CH-5 跨组容灾重试
     * 由 REQ-03 接入 {@code ResolveChannelRouteUseCase} 后替换。无满足渠道抛
     * {@link NoAvailableChannelException}（对齐 RL-1 §4，message 不含敏感信息）。</p>
     */
    private Channel selectChannelPlaceholder(String group, String upstreamModel) {
        return channelRepo.findAll().stream()
                .filter(c -> c.status().isEnabled())
                .filter(c -> group.equals(c.group()))
                .filter(c -> declaresModel(c, upstreamModel))
                .filter(c -> c.baseUrl() != null && !c.baseUrl().isBlank())
                .findFirst()
                .orElseThrow(() -> new NoAvailableChannelException(
                        "no available channel for group/model selection"));
    }

    /** 渠道 models 逗号分隔串是否声明支持给定上游模型 B。 */
    private boolean declaresModel(Channel channel, String upstreamModel) {
        if (channel.models() == null) {
            return false;
        }
        return Arrays.stream(channel.models().split(","))
                .map(String::trim)
                .anyMatch(upstreamModel::equals);
    }

    /**
     * 解析上游响应体的 usage（第15步，D5 token 口径），按 targetProto 选 token 字段名。
     *
     * <p>上游响应体是 targetProto 协议格式：OpenAI 读 {@code usage.prompt_tokens/completion_tokens}，
     * Claude 读 {@code usage.input_tokens/output_tokens}（D5）。仅在上游 2xx 成功后调用（非 2xx 已在
     * RL-3 重试/错误链处置，不进入计费）；解析失败 / 缺 usage → 返回 {@link UsageIR#ZERO}（计费缺失兜底不阻断）。</p>
     */
    private UsageIR parseUsage(UpstreamResponse response, ProtocolFormat targetProto) {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return UsageIR.ZERO;
        }
        try {
            JsonNode usageNode = objectMapper.readTree(body).path("usage");
            if (usageNode.isMissingNode() || usageNode.isNull()) {
                return UsageIR.ZERO;
            }
            if (targetProto == ProtocolFormat.CLAUDE) {
                return UsageIR.of(
                        usageNode.path("input_tokens").asInt(0),
                        usageNode.path("output_tokens").asInt(0));
            }
            return UsageIR.of(
                    usageNode.path("prompt_tokens").asInt(0),
                    usageNode.path("completion_tokens").asInt(0));
        } catch (IOException e) {
            // 上游响应非 JSON（错误页等）：计费 token 归零兜底，不阻断落 Log。
            return UsageIR.ZERO;
        }
    }

    /**
     * 取对外模型 A 的基准售价倍率（BL-7 售价挂 A 恒定）。A 无 PublicModel 记录 → 回落倍率 1（不阻断）。
     */
    private BigDecimal resolveBasePriceRatio(String resolvedPublicModel) {
        return publicModelRepo.findByPublicName(resolvedPublicModel)
                .map(PublicModel::basePriceRatio)
                .orElse(BigDecimal.ONE);
    }

    /**
     * 取分组折扣系数（BL-8 纯折扣，GetGroupRatio(UsingGroup)）。
     *
     * <p>TODO REQ-05 完整：分组折扣应读 {@code group_ratio} KV（BILLING-MODEL-ARCHITECTURE §3，
     * free=1.0/vip=0.85/svip=0.7，后台可配）。中央 GroupRatio 注册中心尚未在本服务落地（见 REQ-13），
     * 本期统一回落 1.0（free 基础折扣口径），售价 = BasePriceRatio(A) × 1.0 × tokens。</p>
     */
    private BigDecimal resolveGroupRatio(String group) {
        return BigDecimal.ONE;
    }

    /**
     * 取实际选中渠道×B 的成本倍率（BL-7 成本挂渠道×B，ADR-BILL-02 不乘 GroupRatio）。
     *
     * <p>主键 {@code (实际 ChannelId, 真实模型 B)} 查 {@link ChannelModelCostRepository}：命中且启用 →
     * 返回 cost_ratio；缺行 / 禁用 → 返回 {@code null}（成本缺失态，由 {@link DualPriceBilling} 兜底
     * quota_cost=0 + costMissing 标记，不阻断）。{@code channelId} 为空（不应发生）亦按缺失处理。</p>
     */
    private BigDecimal resolveCostRatio(Long channelId, String upstreamModel) {
        if (channelId == null) {
            return null;
        }
        return channelModelCostRepo.findByChannelAndUpstream(channelId.intValue(), upstreamModel)
                .filter(c -> Boolean.TRUE.equals(c.enabled()))
                .map(ChannelModelCost::costRatio)
                .orElse(null);
    }

    /**
     * 响应后一次性结算扣减（最小闭环）：按真实 usage 算得的 {@code quota_sell} 扣用户余额。
     *
     * <p>TODO REQ-05 完整：本期无选渠预扣，直接响应后扣售价一次（不做余额下限/欠费保护）。完整的
     * 「选渠后 PreConsume 预扣 + 响应后多退少补 SettleBilling」分段结算待后续接入（§6 第8-9/19步）。
     * 成本/利润不参与扣减（仅落 Log 经营分析），客户只被扣 quota_sell（售价）。</p>
     */
    private void settle(long userId, BillingResult billing) {
        if (billing.quotaSell() <= 0) {
            return;
        }
        userQuotaAccount.debit(userId, Quota.of(billing.quotaSell()));
    }

    /** 落一条消费 Log（RL-7 第⑨步，Type=2 Consume）：三段模型 + 协议三字段 + channel_id + 三金额 + 真实 usage。 */
    private void recordConsumeLog(RelayDispatch dispatch, ModelResolution resolution, Channel channel,
                                  ProtocolFormat targetProto, RelayAuthContext authContext,
                                  UsageIR usage, BillingResult billing) {
        RelayInfo info = new RelayInfo();
        info.setUserId(authContext.userId());
        info.setUsername(authContext.username());
        info.setTokenId(authContext.tokenId());
        info.setTokenName(authContext.tokenName() != null ? authContext.tokenName() : "");
        info.setUsingGroup(authContext.group());
        info.setRequestedModel(resolution.requested());
        info.setResolvedPublicModel(resolution.resolvedPublic());
        info.setUpstreamModelName(resolution.upstream());
        info.setInboundFormat(dispatch.format());
        info.setTargetProtocol(targetProto); // REQ-04: 真实目标协议（按选中渠道 type 判定）
        info.computePassthrough();           // inFmt==targetProto → protocol_converted=false
        info.setChannelId(channel.id());
        info.setChannelType(channel.type().code());
        // 客户端 IP / User-Agent：本期 forward 链路未透传 HttpServletRequest，
        // 落库用空串占位（对齐 logs.ip/user_agent NOT NULL DEFAULT ''，避免 null 违约）。
        // TODO REQ-后续: 从 RelayController 经 RelayAuthContext 透传真实 remoteAddr/UA。
        if (info.ip() == null) {
            info.setIp("");
        }
        if (info.userAgent() == null) {
            info.setUserAgent("");
        }
        // 成本行缺失 → Other 写 cost_missing 诊断标记（看板可筛，不阻断；对齐 RL-7 §4）。
        String other = billing.costMissing() ? "{\"cost_missing\":true}" : null;
        RelayLog log = RelayLog.consume(
                info, usage, billing, System.currentTimeMillis() / 1000L, other);
        logRepo.save(log);
    }

    /**
     * RL-3 单次上游失败处置（re_log + re_ban）：脱敏 → 落错误 Log（Type=5）→ AutoBan 命中则禁用渠道。
     *
     * <p>顺序遵循 PRD RL-3 §3：脱敏（MaskSensitiveErrorWithStatusCode）与禁用判定（AutoBan=1 且命中条件
     * → DisableChannel 复用 CH-3）并行处置，两路汇到错误日志记录。禁用与否都不阻断重试判定（由调用方据
     * {@link RetryPolicy#shouldRetry(int)} 决定换渠道还是终止）。</p>
     *
     * @return 脱敏后的错误描述（供重试耗尽/不可重试时构造对客户错误响应复用）
     */
    private String handleUpstreamFailure(RelayDispatch dispatch, ModelResolution resolution, Channel channel,
                                         RelayAuthContext authContext, int statusCode, String rawDetail) {
        String masked = MaskSensitiveError.mask(statusCode, rawDetail);
        // re_ban：仅 AutoBan=1 且命中禁用条件（401/403）才自动禁用，落 AUTO_DISABLED 并通知 root（CH-3）。
        if (RetryPolicy.shouldAutoDisable(channel.autoBan(), statusCode) && channel.autoDisable()) {
            channelRepo.save(channel);
            notifyChannelAutoDisabled(channel, statusCode);
        }
        // re_log：脱敏后写 Log Type=5 Error（记 channel/model/status_code）。
        recordErrorLog(dispatch, resolution, channel, authContext, masked, statusCode);
        return masked;
    }

    /** 通知 root 渠道已自动禁用（CH-3 通知；本期 server log，告警分发接入见 observability BC）。 */
    private void notifyChannelAutoDisabled(Channel channel, int statusCode) {
        // TODO REQ-后续: 接入 observability AlertNotifierPort 推送 root 告警；本期仅诊断日志占位。
    }

    /**
     * 从上游错误响应体提取原始错误片段（供脱敏输入，非 2xx 时调用）。
     *
     * <p>优先解析 OpenAI/Claude 风格 {@code error.message}；非 JSON / 无该字段则回落响应体文本（后续由
     * {@link MaskSensitiveError} 统一脱敏 + 截断，故此处无需关心敏感性与长度）。</p>
     */
    private String extractErrorDetail(UpstreamResponse response) {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode message = objectMapper.readTree(body).path("error").path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (IOException ignored) {
            // 非 JSON 上游错误页：回落原始文本（脱敏在 MaskSensitiveError 内统一处理）。
        }
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 落一条错误 Log（RL-3 re_log，Type=5 Error）：脱敏 content + channel/model/status_code。 */
    private void recordErrorLog(RelayDispatch dispatch, ModelResolution resolution, Channel channel,
                                RelayAuthContext authContext, String maskedContent, int statusCode) {
        RelayInfo info = new RelayInfo();
        info.setUserId(authContext.userId());
        info.setUsername(authContext.username());
        info.setUsingGroup(authContext.group());
        info.setRequestedModel(resolution.requested());
        info.setResolvedPublicModel(resolution.resolvedPublic());
        info.setUpstreamModelName(resolution.upstream());
        info.setChannelId(channel.id());
        info.setInboundFormat(dispatch.format());
        info.setTargetProtocol(dispatch.format());
        info.computePassthrough();
        // NOT NULL 兜底（logs.token_name/ip/user_agent NOT NULL；token 上下文/请求头可能缺失时补空串）。
        info.setTokenId(authContext.tokenId());
        if (info.tokenName() == null) {
            info.setTokenName(authContext.tokenName() != null ? authContext.tokenName() : "");
        }
        if (info.ip() == null) {
            info.setIp("");
        }
        if (info.userAgent() == null) {
            info.setUserAgent("");
        }
        RelayLog log = RelayLog.error(info, maskedContent, statusCode, System.currentTimeMillis() / 1000L);
        logRepo.save(log);
    }

    /**
     * 按 inFmt 构造对客户的错误响应（RL-3 re_fmt 终态）：脱敏 message + OpenAI/Claude 错误结构。
     *
     * <p>不可重试码直接返回、重试耗尽返回最后错误均经此构造。响应体绝不含上游凭证/URL（已脱敏），
     * statusCode 透传上游错误码（4xx/5xx），让客户拿到与上游一致的语义。Gemini 等未支持协议回落
     * OpenAI 结构。</p>
     */
    private RelayForwardResult buildErrorResult(RelayDispatch dispatch, int statusCode, String maskedMessage) {
        byte[] body;
        try {
            ObjectNode errorBody = objectMapper.createObjectNode();
            if (dispatch.format() == com.nexa.relay.domain.vo.ProtocolFormat.CLAUDE) {
                // Claude 错误结构：{type:"error", error:{type, message}}。
                errorBody.put("type", "error");
                ObjectNode err = errorBody.putObject("error");
                err.put("type", claudeErrorType(statusCode));
                err.put("message", maskedMessage);
            } else {
                // OpenAI 错误结构（默认/回落）：{error:{message, type, code}}。
                ObjectNode err = errorBody.putObject("error");
                err.put("message", maskedMessage);
                err.put("type", "upstream_error");
                err.put("code", statusCode);
            }
            body = objectMapper.writeValueAsBytes(errorBody);
        } catch (IOException e) {
            body = ("{\"error\":{\"message\":\"" + maskedMessage + "\"}}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return new RelayForwardResult(
                statusCode,
                java.util.Map.of("Content-Type", java.util.List.of("application/json")),
                body);
    }

    /** Claude 错误结构 error.type 取值（按状态码映射，对齐 Anthropic 错误类型语义）。 */
    private String claudeErrorType(int statusCode) {
        return switch (statusCode) {
            case 401, 403 -> "authentication_error";
            case 429 -> "rate_limit_error";
            case 400 -> "invalid_request_error";
            default -> "api_error";
        };
    }

    /** 重试上限（common.RetryTimes，本期取 RetryPolicy 默认值；后续可由配置覆盖）。 */
    private int maxRetryTimes() {
        return RetryPolicy.DEFAULT_MAX_RETRIES;
    }
}
