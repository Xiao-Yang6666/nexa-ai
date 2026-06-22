package com.nexa.account.application.port;

import com.nexa.account.domain.vo.Email;

import java.util.Optional;

/**
 * 密码重置令牌服务端口（应用层依赖，基础设施层实现）。
 *
 * <p>承载找回密码两段式流程的令牌「签发暂存」与「校验消费」（F-1006 发件、F-1007 提交重置）。
 * 令牌与有效期是带状态的副作用资源（非领域聚合状态），抽象为应用层端口；本切片内存实现 + TODO 切 Redis。
 * 令牌绑定邮箱并带 TTL（PRD AC-3「重置令牌带过期时间」）。</p>
 */
public interface PasswordResetTokenService {

    /**
     * 为指定邮箱签发并暂存一枚一次性重置令牌（带 TTL）。
     *
     * <p>同邮箱重复申请覆盖旧令牌（以最新为准）。返回的令牌随重置邮件链接下发给用户。</p>
     *
     * @param email 目标邮箱（已确认为已注册邮箱）
     * @return 新签发的不透明令牌字符串
     */
    String issue(Email email);

    /**
     * 校验令牌是否对该邮箱有效且未过期，通过后<b>消费</b>（一次性失效）。
     *
     * <p>校验通过即删除暂存，防重放（PRD AC-3 F7「令牌有效且未过期」+ 一次性）。
     * 失败（无暂存/不匹配/过期）不消费，返回空。</p>
     *
     * @param email 提交重置时携带的邮箱
     * @param token 提交重置时携带的令牌
     * @return 校验通过返回该邮箱（便于调用方据邮箱定位用户）；失败返回空
     */
    Optional<Email> verifyAndConsume(Email email, String token);
}
