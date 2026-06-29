package com.nexa.application.playground;

import com.nexa.application.playground.port.PlaygroundRelayPort;
import com.nexa.application.playground.port.PlaygroundUserGroupPort;
import com.nexa.domain.playground.exception.InvalidPlaygroundRequestException;
import com.nexa.domain.playground.model.PlaygroundChatRequest;
import com.nexa.domain.playground.vo.CredentialKind;
import com.nexa.domain.playground.vo.PlaygroundResult;
import com.nexa.domain.playground.vo.TempTokenContext;
import org.springframework.stereotype.Service;

/**
 * 站内对话试用用例（应用层编排，F-4038）。
 *
 * <p>编排站内试用一笔 chat/completions 的端到端流程（薄壳，业务规则在 domain 层）：
 * <ol>
 *   <li>解析用户当前分组——经 {@link PlaygroundUserGroupPort} 取 {@code UsingGroup}（缺失即非法请求）；</li>
 *   <li>构造并校验请求聚合 {@link PlaygroundChatRequest}（入参完整性在聚合根构造期守护）；</li>
 *   <li>授权——{@code request.authorize()} 守 F-4038 安全闸（禁用 access token，access token → 403）；</li>
 *   <li>构造临时令牌上下文——{@code request.toTempTokenContext()} 产出 {@code playground-<group>}；</li>
 *   <li>经 {@link PlaygroundRelayPort} 走 relay 转发（透传上游响应，按用户实际额度计费，落 Log，C→A 客户视图）。</li>
 * </ol>
 * 应用层不写授权/上下文/校验规则（在聚合根/值对象上），不写 relay 转发逻辑（在 relay BC，经端口解耦），
 * 符合 backend-engineer §2.1「application 薄、不含领域规则」。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §13 F-4038。</p>
 */
@Service
public class RunPlaygroundChatUseCase {

    private final PlaygroundRelayPort relayPort;
    private final PlaygroundUserGroupPort userGroupPort;

    /**
     * @param relayPort     中继执行端口（infra 提供桥接 relay BC 的适配器）
     * @param userGroupPort 用户分组查询端口（infra 提供桥接 account BC 的适配器）
     */
    public RunPlaygroundChatUseCase(PlaygroundRelayPort relayPort, PlaygroundUserGroupPort userGroupPort) {
        this.relayPort = relayPort;
        this.userGroupPort = userGroupPort;
    }

    /**
     * 执行一笔站内试用对话。
     *
     * @param userId         当前登录用户 id（来自认证主体）
     * @param username       用户名（落 Log 可读性，可空）
     * @param requestedModel 客户输入模型名 C（从请求体解析）
     * @param stream         是否流式（从请求体解析）
     * @param hasMessages    {@code messages[]} 是否非空（从请求体解析，domain 不碰原始正文）
     * @param credentialKind 请求凭据类型（接口层据 Authorization Bearer 头映射：Bearer→ACCESS_TOKEN，否则 SESSION）
     * @param rawBody        客户原始 OpenAI chat/completions 请求体（透传给 relay）
     * @return 透传上游的响应结果（流式/非流式，客户视图脱敏）
     * @throws com.nexa.domain.playground.exception.PlaygroundAccessDeniedException 凭据为 access token（→403）
     * @throws InvalidPlaygroundRequestException 入参非法 / 用户分组缺失
     */
    public PlaygroundResult execute(long userId, String username, String requestedModel,
                                    boolean stream, boolean hasMessages,
                                    CredentialKind credentialKind, byte[] rawBody) {
        // ① 取用户当前使用分组；缺失则按非法请求处理（分组决定计费/选渠口径，不默认兜底以免错算额度）。
        String group = userGroupPort.groupOf(userId)
                .orElseThrow(() -> new InvalidPlaygroundRequestException(
                        "user using-group is not available for playground"));

        // ② 构造并校验请求聚合（model/messages/group 完整性在聚合根构造期守护）。
        PlaygroundChatRequest request = PlaygroundChatRequest.of(
                userId, username, group, requestedModel, stream, hasMessages, credentialKind);

        // ③ F-4038 关键安全闸：先授权再做任何 relay 动作，access token 直接 403，不进入计费/转发。
        request.authorize();

        // ④ 构造 playground-<group> 临时令牌上下文（按用户 UsingGroup）。
        TempTokenContext context = request.toTempTokenContext();

        // ⑤ 经端口走 relay：协议转换/选渠/调上游/双价计费/落 Log 全在 relay BC，本用例不重复。
        return relayPort.forwardChatCompletion(
                context, request.requestedModel(), request.isStream(), rawBody);
    }
}
