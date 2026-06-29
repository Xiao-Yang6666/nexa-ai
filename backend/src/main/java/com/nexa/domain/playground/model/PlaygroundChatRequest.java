package com.nexa.domain.playground.model;

import com.nexa.domain.playground.exception.InvalidPlaygroundRequestException;
import com.nexa.domain.playground.vo.CredentialKind;
import com.nexa.domain.playground.vo.TempTokenContext;

/**
 * 站内对话试用请求聚合根（F-4038，充血模型）。
 *
 * <p>一次 Playground 试用请求的领域不变量守护者：封装「谁（userId/group）、用什么凭据（credentialKind）、
 * 请求哪个模型（requestedModel）、是否流式（stream）」，并在本聚合根方法上守护两条核心规则：
 * <ol>
 *   <li><b>禁用 access token</b>（F-4038 关键安全闸）——{@link #authorize()} 调
 *       {@link CredentialKind#requireSessionForPlayground()}，access token → 403；</li>
 *   <li><b>入参完整性</b>——构造期校验 model/messages 非空（openapi {@code OpenAIChatCompletionRequest} 必填）。</li>
 * </ol>
 * 通过校验后，{@link #toTempTokenContext()} 产出 {@code playground-<group>} 临时令牌上下文，交应用层走 relay。</p>
 *
 * <p>充血而非贫血：授权与上下文构造是聚合根<b>行为</b>（{@code request.authorize()} / {@code request.toTempTokenContext()}），
 * 应用层只编排不写规则（backend-engineer §2.2）。本聚合根零框架依赖，可纯 JUnit 单测。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §13 F-4038 + prd Playground。</p>
 */
public final class PlaygroundChatRequest {

    private final long userId;
    private final String username;
    private final String usingGroup;
    private final String requestedModel;
    private final boolean stream;
    private final boolean hasMessages;
    private final CredentialKind credentialKind;

    private PlaygroundChatRequest(long userId, String username, String usingGroup,
                                  String requestedModel, boolean stream, boolean hasMessages,
                                  CredentialKind credentialKind) {
        this.userId = userId;
        this.username = username;
        this.usingGroup = usingGroup;
        this.requestedModel = requestedModel;
        this.stream = stream;
        this.hasMessages = hasMessages;
        this.credentialKind = credentialKind;
    }

    /**
     * 构造并校验一次试用请求（入参完整性在构造期守护，不允许半成品聚合根存在）。
     *
     * @param userId         登录用户 id（必 &gt; 0）
     * @param username       用户名（落 Log 可读性，可空）
     * @param usingGroup     用户当前使用分组（非空白）
     * @param requestedModel 客户输入模型名 C（非空白，openapi 必填）
     * @param stream         是否流式
     * @param hasMessages    {@code messages[]} 是否非空（openapi 必填，接口层解析后传入布尔，domain 不碰原始正文）
     * @param credentialKind 请求凭据类型（session / access token）
     * @return 已校验的试用请求聚合
     * @throws InvalidPlaygroundRequestException model 空 / messages 空 / group 空
     * @throws IllegalArgumentException          userId 非正或 credentialKind 为空
     */
    public static PlaygroundChatRequest of(long userId, String username, String usingGroup,
                                           String requestedModel, boolean stream, boolean hasMessages,
                                           CredentialKind credentialKind) {
        if (userId <= 0) {
            throw new IllegalArgumentException("playground request userId must be positive, got " + userId);
        }
        if (credentialKind == null) {
            throw new IllegalArgumentException("playground request credentialKind must not be null");
        }
        if (usingGroup == null || usingGroup.isBlank()) {
            // 分组决定计费/选渠口径，缺失即非法请求（而非默认兜底，避免错算额度）。
            throw new InvalidPlaygroundRequestException("usingGroup is required for playground");
        }
        if (requestedModel == null || requestedModel.isBlank()) {
            throw new InvalidPlaygroundRequestException("model is required");
        }
        if (!hasMessages) {
            throw new InvalidPlaygroundRequestException("messages must not be empty");
        }
        return new PlaygroundChatRequest(userId, username, usingGroup.trim(),
                requestedModel.trim(), stream, true, credentialKind);
    }

    /**
     * 授权本次试用（F-4038 关键安全闸：禁用 access token）。
     *
     * <p>必须在走 relay <b>之前</b>调用。access token 凭据 → {@link com.nexa.domain.playground.exception.PlaygroundAccessDeniedException}
     * （403「暂不支持使用 access token」）。</p>
     *
     * @throws com.nexa.domain.playground.exception.PlaygroundAccessDeniedException 凭据为 access token
     */
    public void authorize() {
        credentialKind.requireSessionForPlayground();
    }

    /**
     * 产出站内试用临时令牌上下文（{@code playground-<group>}）。
     *
     * <p>调用前应先 {@link #authorize()} 通过——本方法不重复守闸，职责单一（授权 vs 上下文构造分离）。</p>
     *
     * @return 临时令牌上下文，交应用层经 SetupContextForToken 注入 relay
     */
    public TempTokenContext toTempTokenContext() {
        return TempTokenContext.forUser(userId, usingGroup);
    }

    /** @return 登录用户 id */
    public long userId() {
        return userId;
    }

    /** @return 用户名（可空，非鉴权依据） */
    public String username() {
        return username;
    }

    /** @return 使用分组 */
    public String usingGroup() {
        return usingGroup;
    }

    /** @return 客户输入模型名 C */
    public String requestedModel() {
        return requestedModel;
    }

    /** @return 是否流式 */
    public boolean isStream() {
        return stream;
    }

    /** @return 请求凭据类型 */
    public CredentialKind credentialKind() {
        return credentialKind;
    }
}
