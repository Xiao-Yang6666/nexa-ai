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
import com.nexa.relay.domain.ir.UsageIR;
import com.nexa.relay.domain.model.RelayLog;
import com.nexa.relay.domain.port.UpstreamHttpPort;
import com.nexa.relay.domain.port.UpstreamRequest;
import com.nexa.relay.domain.port.UpstreamResponse;
import com.nexa.relay.domain.repository.PlatformModelMappingRepository;
import com.nexa.relay.domain.repository.RelayLogRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.service.DualPriceBilling;
import com.nexa.relay.domain.service.RelayPathResolver;
import com.nexa.relay.domain.service.TwoLayerModelResolver;
import com.nexa.relay.domain.vo.AliasScope;
import com.nexa.relay.domain.vo.BillingResult;
import com.nexa.routing.application.SelectRelayChannelUseCase;
import com.nexa.relay.domain.vo.ModelResolution;
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

        // ④ 选渠（group×B → Ability 加权随机 + CH-4 亲和 + CH-5 重试）。
        // REQ-03: 经 SelectRelayChannelUseCase 按 Ability 表 group×B 加权随机选中完整 Channel 聚合；
        //         无候选抛 NoAvailableChannelException。CH-4 亲和 / CH-5 重试循环由 REQ-09/10 接入。
        Channel channel = selectRelayChannelUseCase.selectChannel(authContext.group(), upstreamModel);

        // ⑤ 协议转换 + 调上游（RL-6 头尾决策 + RL-7 第⑤步）。
        // TODO REQ-04: 接入 ProtocolRegistry 做 inFmt==targetProto passthrough / 否则 IR 转换；
        //              本期仅 OpenAI 非流式 passthrough——仅改写 body 的 model 字段为 B 后透传。
        byte[] upstreamBody = rewriteModel(root, upstreamModel);
        UpstreamRequest upstreamRequest = UpstreamRequest.of(
                "POST", channel.baseUrl(), path, channel.key(), upstreamBody,
                java.util.Map.of("Content-Type", "application/json"));
        UpstreamResponse upstreamResponse = upstreamHttpPort.send(upstreamRequest);

        // ⑥⑦ 双价记账（quota_sell/quota_cost/quota_profit，REQ-05）。
        // 第15步：从上游响应体解析真实 usage（D5 prompt/completion tokens）。
        UsageIR usage = parseUsage(upstreamResponse);
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

        // ⑨ 落 Log（RL-7 第⑨步，Type=2 Consume）：含 C/A/B 三段 + 协议三字段 + channel_id + 三金额。
        recordConsumeLog(dispatch, resolution, channel, authContext, usage, billing);

        // 透传上游 status + headers + body 回客户（非流式 passthrough）。
        return new RelayForwardResult(
                upstreamResponse.statusCode(), upstreamResponse.headers(), upstreamResponse.body());
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

    /** 改写请求体 model 字段为真实上游名 B（OpenAI 非流式 passthrough，其余字段透传）。 */
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
     * 解析上游响应体的 usage（第15步，D5 token 口径）。
     *
     * <p>本期 OpenAI 非流式 passthrough：上游响应体即 OpenAI 格式，直接读 {@code usage.prompt_tokens}/
     * {@code usage.completion_tokens}。解析失败 / 缺 usage / 非 2xx → 返回 {@link UsageIR#ZERO}
     * （计费缺失兜底不阻断；非 2xx 上游错误本期仍按 0 token 落消费 Log，完整错误处置见 REQ-09）。</p>
     */
    private UsageIR parseUsage(UpstreamResponse response) {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return UsageIR.ZERO;
        }
        try {
            JsonNode usageNode = objectMapper.readTree(body).path("usage");
            if (usageNode.isMissingNode() || usageNode.isNull()) {
                return UsageIR.ZERO;
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
                                  RelayAuthContext authContext, UsageIR usage, BillingResult billing) {
        RelayInfo info = new RelayInfo();
        info.setUserId(authContext.userId());
        info.setUsername(authContext.username());
        info.setTokenId(authContext.tokenId());
        info.setTokenName(authContext.tokenName());
        info.setUsingGroup(authContext.group());
        info.setRequestedModel(resolution.requested());
        info.setResolvedPublicModel(resolution.resolvedPublic());
        info.setUpstreamModelName(resolution.upstream());
        info.setInboundFormat(dispatch.format());
        info.setTargetProtocol(dispatch.format()); // 本期仅 OpenAI passthrough：inFmt == targetProto
        info.computePassthrough();
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
}
