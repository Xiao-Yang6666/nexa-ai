package com.nexa.infrastructure.playground.relay;

import com.nexa.application.playground.port.PlaygroundRelayPort;
import com.nexa.domain.playground.vo.PlaygroundResult;
import com.nexa.domain.playground.vo.TempTokenContext;
import com.nexa.application.relay.RelayForwardUseCase;
import com.nexa.domain.relay.vo.ModelResolution;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Playground 中继端口的 relay BC 桥接实现（基础设施层，F-4038）。
 *
 * <p>实现 {@link PlaygroundRelayPort}，把 Playground 的「以临时令牌走 relay」请求桥接到 relay BC 的
 * {@code RelayForwardUseCase}：以 {@code playground-<group>} 上下文做两层模型映射（C→A→B），随后交 relay
 * 完成转发/计费/落 Log。</p>
 *
 * <p>本期与 relay 转发链路一致——relay 的完整 HTTP client + 流式 SSE 转发尚为 W3+ 骨架（见
 * {@code RelayController#forwardRelay} TODO），故本适配器同步返回「尚未接通」骨架结果，确保 Playground
 * 端到端编排、安全闸（禁 access token）与映射先行可编译可验证；relay 转发接通后本类零改动即透传真实响应。</p>
 *
 * <p>跨 context 依赖方向：infra 依赖 relay BC 的 application 用例（向内依赖具体实现属 infra 职责，
 * 不污染 Playground domain/application，backend-engineer §2.1）。</p>
 */
@Component
public class RelayBridgePlaygroundAdapter implements PlaygroundRelayPort {

    private final RelayForwardUseCase relayForwardUseCase;

    /**
     * @param relayForwardUseCase relay 中继转发用例（relay BC 应用层）
     */
    public RelayBridgePlaygroundAdapter(RelayForwardUseCase relayForwardUseCase) {
        this.relayForwardUseCase = relayForwardUseCase;
    }

    /**
     * {@inheritDoc}
     *
     * <p>以临时令牌上下文执行两层模型映射后交 relay 转发。relay 转发链路接通前返回 503 骨架响应
     * （OpenAI 兼容错误信封，不泄露上游细节）。</p>
     */
    @Override
    public PlaygroundResult forwardChatCompletion(TempTokenContext context,
                                                  String requestedModel,
                                                  boolean stream,
                                                  byte[] rawBody) {
        // 以 playground-<group> 上下文做 C→A→B 两层映射（复用 relay BC 既有领域服务，不重写映射规则）。
        ModelResolution resolution = relayForwardUseCase.resolveModel(
                requestedModel, context.userId(), context.group());

        // TODO(W3+ relay 转发接通): 调 RelayForwardUseCase 完整链路（选渠→协议转换→调上游→双价计费→落 Log），
        //   将上游响应字节透传回 PlaygroundResult（流式则 PlaygroundResult.sse，否则 .json）。relay 转发
        //   骨架未接通期间返回 503，保证 Playground 端到端可编译可验证（安全闸/映射已实际生效）。
        String publicModel = resolution.resolvedPublic();
        String body = "{\"error\":{\"type\":\"server_error\","
                + "\"message\":\"playground relay forwarding not yet wired (W3+ TODO)\","
                + "\"code\":\"RELAY_NOT_WIRED\"},"
                + "\"resolved_public_model\":\"" + publicModel + "\"}";
        return PlaygroundResult.json(503, body.getBytes(StandardCharsets.UTF_8));
    }
}
