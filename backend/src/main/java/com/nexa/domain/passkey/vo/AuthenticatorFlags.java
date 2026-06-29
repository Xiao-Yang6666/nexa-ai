package com.nexa.domain.passkey.vo;

/**
 * Authenticator 行为标志值对象（WebAuthn authenticator data flags 的领域投影）。
 *
 * <p>不可变、按值相等。聚合断言/注册过程中 authenticator 上报的几个布尔标志，落库供审计与策略判断
 * （对齐 DB-SCHEMA §16 {@code user_present/user_verified/backup_eligible/backup_state}）：
 * <ul>
 *   <li>{@code userPresent}（UP）：用户在场（物理交互，如触摸）。</li>
 *   <li>{@code userVerified}（UV）：用户已被验证（PIN/生物特征）——二次验证场景要求为真。</li>
 *   <li>{@code backupEligible}（BE）：凭据可被备份（多设备同步资格）。</li>
 *   <li>{@code backupState}（BS）：凭据当前已备份（已同步到多设备）。</li>
 * </ul></p>
 *
 * <p>领域规则出处：WebAuthn L3 §6.1 Authenticator Data flags。</p>
 *
 * @param userPresent    用户在场（UP）
 * @param userVerified   用户已验证（UV）
 * @param backupEligible 可备份资格（BE）
 * @param backupState    已备份状态（BS）
 */
public record AuthenticatorFlags(
        boolean userPresent,
        boolean userVerified,
        boolean backupEligible,
        boolean backupState) {

    /**
     * 默认标志（仅 UP=true）：常用于无可信标志来源时的保守缺省。
     *
     * @return 仅用户在场为真的标志组合
     */
    public static AuthenticatorFlags userPresentOnly() {
        return new AuthenticatorFlags(true, false, false, false);
    }
}
