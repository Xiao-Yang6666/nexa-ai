package com.nexa.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
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

    public RelayForwardUseCase(PlatformModelMappingRepository l2Repo,
                               UserModelAliasRepository l1Repo,
                               RelayLogRepository logRepo,
                               UpstreamHttpPort upstreamHttpPort,
                               ChannelRepository channelRepo,
                               ObjectMapper objectMapper,
                               KeyLimitGuard keyLimitGuard,
                               SelectRelayChannelUseCase selectRelayChannelUseCase) {
        this.l2Repo = l2Repo;
        this.l1Repo = l1Repo;
        this.logRepo = logRepo;
        this.upstreamHttpPort = upstreamHttpPort;
        this.channelRepo = channelRepo;
        this.objectMapper = objectMapper;
        this.keyLimitGuard = keyLimitGuard;
        this.selectRelayChannelUseCase = selectRelayChannelUseCase;
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

        // ⑥⑦ 双价记账（quota_sell/quota_cost/quota_profit）。
        // TODO REQ-05: 接入 DualPriceBilling（售价用 A×GroupRatio、成本用 ChannelModelCost(ChannelId,B)）+ 预扣/结算。
        BillingResult billing = new BillingResult(0, 0, 0, true);

        // ⑨ 落 Log（RL-7 第⑨步，Type=2 Consume）。
        recordConsumeLog(dispatch, resolution, channel, authContext, billing);

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

    /** 落一条消费 Log（RL-7 第⑨步，最小填充能编译/落库即可；完整计费口径由 REQ-05 补全）。 */
    private void recordConsumeLog(RelayDispatch dispatch, ModelResolution resolution, Channel channel,
                                  RelayAuthContext authContext, BillingResult billing) {
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
        // TODO REQ-05: usage 取自 IR（D5 解析上游 usage），billing 取 DualPriceBilling 真实结果。
        RelayLog log = RelayLog.consume(
                info, UsageIR.ZERO, billing, System.currentTimeMillis() / 1000L, null);
        logRepo.save(log);
    }
}
