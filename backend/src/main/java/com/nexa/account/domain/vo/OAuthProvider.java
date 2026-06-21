package com.nexa.account.domain.vo;

import com.nexa.account.domain.exception.InvalidCredentialException;

/**
 * OAuth 第三方身份提供商（值对象 / 枚举）。
 *
 * <p>本枚举收纳<b>内建</b> provider：GitHub / Discord / OIDC / LinuxDO（F-1016~1020）+ WeChat（F-1021/1022，
 * 扫码授权，本切片接入）。Telegram 走独立的 HMAC 校验路径（F-1051/1052，非标准 OAuth 授权码流程，
 * 不入本枚举）。自定义 provider（{@code com.nexa.oauthprovider} 上下文的 CustomOAuthProvider 表）以整数
 * provider_id 路由、由独立用例处理（F-1025），也不入本内建枚举。</p>
 *
 * <p>领域规则来源：openapi.yaml {@code /api/oauth/{provider}}（F-1016 通用回调，provider 为路径段）、
 * {@code /api/oauth/discord}（F-1018）、{@code /api/oauth/linuxdo}（F-1020）；
 * DB-SCHEMA §1 User 上的 {@code github_id/discord_id/oidc_id/linux_do_id} 索引列、
 * §13 UserOAuthBinding 绑定表。{@link #code()} 为<b>稳定的 provider 标识串</b>，用于路由分发、
 * 落库 {@code provider} 列、与前端约定的回调路径段一致。</p>
 *
 * <p>设计说明（对齐 DB-SCHEMA §13 的取舍）：DB-SCHEMA §13 的 {@code user_oauth_bindings.provider_id}
 * 为整数，外键指向现网 {@code CustomOAuthProvider} 表（自定义 provider，非本批范围）。本切片绑定的是
 * 5 个内建 provider，没有 CustomOAuthProvider 行可引用，故绑定表用<b>字符串 provider 标识</b>
 * （本枚举 {@code code()}）落库，语义更直接、零外键依赖；待下一片接入自定义 provider 时再扩展。
 * 这一处对 §13 的合理偏离已在绑定表迁移脚本与 JPA 实体注释中标注。</p>
 */
public enum OAuthProvider {

    /** GitHub OAuth（F-1016/F-1017，含 legacy github_id 迁移）。 */
    GITHUB("github"),

    /** Discord OAuth（F-1018）。 */
    DISCORD("discord"),

    /** OIDC 通用 OAuth（F-1019）。 */
    OIDC("oidc"),

    /** LinuxDO OAuth（F-1020，授权后另含信任级校验）。 */
    LINUXDO("linuxdo"),

    /** WeChat 扫码授权（F-1021/1022，二维码 + 轮询，bind 端点用授权码完成登录/绑定）。 */
    WECHAT("wechat");

    private final String code;

    OAuthProvider(String code) {
        this.code = code;
    }

    /**
     * @return 稳定的 provider 标识串（落库 {@code provider} 列、回调路径段、路由 key）
     */
    public String code() {
        return code;
    }

    /**
     * 由标识串解析 provider（路由分发 / 落库还原用）。
     *
     * <p>大小写不敏感并 trim，便于接口层直接拿路径段（如 {@code /api/oauth/GitHub}）解析。
     * 未知 provider 抛 {@link InvalidCredentialException}（接口层映射 400），不静默兜底，
     * 避免把非法 provider 当成某个默认值误处理。</p>
     *
     * @param raw provider 标识串（路径段 / 落库值）
     * @return 匹配的枚举
     * @throws InvalidCredentialException 当 {@code raw} 为空或不是受支持的内建 provider 时
     */
    public static OAuthProvider fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidCredentialException("oauth provider must not be blank");
        }
        String normalized = raw.trim().toLowerCase();
        for (OAuthProvider p : values()) {
            if (p.code.equals(normalized)) {
                return p;
            }
        }
        // 不支持的 provider（含 wechat/telegram/自定义）在本切片直接拒绝，留待后续 wave。
        throw new InvalidCredentialException("unsupported oauth provider: " + normalized);
    }
}
