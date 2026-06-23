package com.nexa.playground.domain.vo;

import com.nexa.playground.domain.exception.PlaygroundAccessDeniedException;

/**
 * 请求凭据类型（值对象，F-4038 access token 安全闸的领域表达）。
 *
 * <p>站内 Playground 只接受 <b>登录会话</b>（{@link #SESSION}）凭据；以 access token
 * （{@link #ACCESS_TOKEN}，即 {@code Authorization: Bearer} API key）调用一律拒绝。该判定是
 * 关键安全闸——把「凭据从哪来」这一鉴权事实抽成领域值对象，使「禁用 access token」规则可在
 * domain 层纯单测，不依赖 HTTP/框架（backend-engineer §2.1/§2.2）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §13 F-4038「{@code use_access_token}→{@code ErrorCodeAccessDenied}」
 * + prd Playground「禁用 access token 是关键安全闸」。接口层据「凭据是否来自 Authorization Bearer 头」
 * 映射本枚举（Bearer→ACCESS_TOKEN，session cookie→SESSION），再调 {@link #requireSessionForPlayground()}
 * 守闸。</p>
 */
public enum CredentialKind {

    /** 登录会话凭据（session cookie），Playground 唯一放行的凭据来源。 */
    SESSION,

    /** API access token（{@code Authorization: Bearer}），Playground 禁用。 */
    ACCESS_TOKEN;

    /**
     * 守护 Playground 凭据闸：非 session 凭据即拒绝（命令版护栏，充血模型）。
     *
     * <p>为何护栏挂在值对象上而非散落到 controller：使「Playground 禁 access token」这条业务规则
     * 有唯一领域归属点，可单测、不重复。</p>
     *
     * @throws PlaygroundAccessDeniedException 当前为 {@link #ACCESS_TOKEN}（→403「暂不支持使用 access token」）
     */
    public void requireSessionForPlayground() {
        if (this == ACCESS_TOKEN) {
            throw new PlaygroundAccessDeniedException();
        }
    }

    /**
     * 是否为 Playground 放行的凭据（不抛异常的查询版）。
     *
     * @return session 返回 {@code true}
     */
    public boolean allowedForPlayground() {
        return this == SESSION;
    }
}
