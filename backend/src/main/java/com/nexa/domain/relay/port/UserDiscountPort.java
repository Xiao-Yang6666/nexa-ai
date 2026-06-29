package com.nexa.domain.relay.port;

import java.math.BigDecimal;

/**
 * 用户专属折扣端口（domain 定接口，infrastructure 由 account BC 实现，REQ-05 计费）。
 *
 * <p>售价侧的「用户级专属折扣」接入点：在分组倍率之后再乘一道用户折扣（{@code User.discountRatio}），
 * 实现「同一分组档位下，给特定大客户再让利」。relay 域只依赖本端口拿「某 userId 的专属折扣」，
 * 用户聚合的查询细节封装在 {@code com.nexa.account...UserDiscountAdapter}（依赖倒置，relay 不编译期
 * 耦合 account 内部）。</p>
 *
 * <p>计费语义：售价 {@code quota_sell = BasePriceRatio(A) × 分组倍率 × 用户折扣 × tokens}（用户折扣只作用于
 * 售价侧，不进成本）。用户不存在 / 未设折扣时返回 {@code 1.0}（不打折，保持旧行为，不阻断计费）。</p>
 */
public interface UserDiscountPort {

    /**
     * 取指定用户的专属折扣系数。
     *
     * @param userId 调用方用户 id
     * @return 用户专属折扣（>=0）；用户不存在/未设折扣返回 {@code 1.0}
     */
    BigDecimal discountOf(long userId);
}
