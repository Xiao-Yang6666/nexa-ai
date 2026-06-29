package com.nexa.application.compliance;

/**
 * 账号注销命令（应用层入参，F-5020）。
 *
 * <p>self-scope 注销：注销的目标恒为「操作者本人」。接口层从会话/令牌解析出本人 {@code userId}
 * 后构造本命令，<b>不</b>接受外部传入任意 userId（杜绝越权注销他人账号）。</p>
 *
 * @param userId 注销目标（= 操作者本人 id，由会话解析，&gt; 0）
 */
public record DeactivateAccountCommand(long userId) {

    /**
     * 紧凑构造校验：userId 必为正。
     *
     * @throws IllegalArgumentException userId 非正（脏入参，接口层应已由鉴权保证）
     */
    public DeactivateAccountCommand {
        if (userId <= 0) {
            throw new IllegalArgumentException("deactivate account userId must be positive");
        }
    }
}
