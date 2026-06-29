package com.nexa.application.account.port;

import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.VerificationCode;

/**
 * 邮箱验证码服务端口（应用层依赖，基础设施层实现）。
 *
 * <p>承载注册/找回验证码的「下发暂存」与「校验消费」两个动作（F-1004 发码、F-1005 校验）。
 * 验证码与有效期是带状态的副作用资源（Redis/内存），不属领域聚合状态，故抽象为应用层端口，
 * 实现可换（本切片内存实现 + TODO 切 Redis），用例不感知存储细节（backend-engineer §2.3）。</p>
 */
public interface VerificationCodeService {

    /**
     * 为指定邮箱签发并暂存一枚验证码（带 TTL）。
     *
     * <p>同一邮箱重复请求时覆盖旧码（以最新为准）；TTL 由实现按 PRD 约定设定（如 10 分钟）。</p>
     *
     * @param email 目标邮箱
     * @return 新签发的验证码（供发信端口投递给用户）
     */
    VerificationCode issue(Email email);

    /**
     * 校验某邮箱的验证码是否匹配且未过期，校验通过后<b>消费</b>（一次性失效）。
     *
     * <p>校验通过即删除暂存，防止同一码被重放（PRD AC-1 R7「匹配且未过期」语义 + 一次性消费）。
     * 校验失败（无暂存/不匹配/已过期）不消费、返回 {@code false}。</p>
     *
     * @param email 目标邮箱
     * @param code  待校验验证码
     * @return 匹配且未过期返回 {@code true}（并已消费），否则 {@code false}
     */
    boolean verifyAndConsume(Email email, VerificationCode code);
}
