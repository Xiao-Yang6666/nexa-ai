package com.nexa.application.playground.port;

import com.nexa.domain.playground.vo.PlaygroundResult;
import com.nexa.domain.playground.vo.TempTokenContext;

/**
 * Playground 中继执行端口（应用层，DDD §2.3 接口抽象）。
 *
 * <p>抽象「以临时令牌上下文 {@code playground-<group>} 经 relay 转发一笔 chat/completions 并按用户实际
 * 额度计费」这一动作，使 Playground 应用层<b>不</b>感知 relay 的 HTTP client / 协议转换 / 选渠 / 计费细节。
 * 基础设施层（{@code infrastructure}）提供桥接 relay BC（{@code RelayForwardUseCase}）的适配器实现。</p>
 *
 * <p>为何用端口而非直接依赖 relay 用例：Playground 与 Relay 是两个 bounded context，跨 context 调用经
 * 端口解耦（domain/application 只依赖本接口），便于单测注入桩、避免 context 间硬耦合（backend-engineer §2.5
 * 模块化单体内的 context 边界）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §13 F-4038「以 tempToken 经 SetupContextForToken 后调 Relay，
 * 透传上游响应，按用户实际额度计费（落 Log，C→A 客户视图）」。</p>
 */
public interface PlaygroundRelayPort {

    /**
     * 以临时令牌上下文走 relay 转发一笔 chat/completions。
     *
     * @param context        临时令牌上下文（{@code playground-<group>}）
     * @param requestedModel 客户输入模型名 C
     * @param stream         是否流式
     * @param rawBody        客户原始 OpenAI chat/completions 请求体（透传给 relay）
     * @return 透传上游的响应结果（已计费、已落 Log、客户视图脱敏）
     */
    PlaygroundResult forwardChatCompletion(TempTokenContext context,
                                           String requestedModel,
                                           boolean stream,
                                           byte[] rawBody);
}
