package com.nexa.passkey.application.port;

/**
 * WebAuthn challenge 暂存端口（应用层依赖，基础设施层实现）。
 *
 * <p>WebAuthn ceremony 是两段式：begin 生成一次性 challenge 下发前端、finish 校验前端回传的响应是否对应
 * 该 challenge。challenge 必须<b>一次性消费</b>且有<b>有效期</b>（防重放）。本端口抽象其存储，便于实现替换
 * （内存 / Redis）与单测注入（backend-engineer §2.3）。</p>
 *
 * <p>key 约定：以「用途 + 会话标识」为键（如 {@code register:userId} / {@code login:sessionToken}），
 * 由实现/调用方约定，端口本身只管存取与一次性消费。</p>
 */
public interface PasskeyChallengeStore {

    /**
     * 暂存一次性 challenge（覆盖同 key 旧值）。
     *
     * @param key       challenge 归属键（用途+会话标识）
     * @param challenge 一次性随机 challenge（base64url）
     */
    void put(String key, String challenge);

    /**
     * 取出并消费 challenge（一次性：取出即删除，防重放）。
     *
     * @param key challenge 归属键
     * @return 命中且未过期返回 challenge，否则 {@code null}
     */
    String consume(String key);
}
