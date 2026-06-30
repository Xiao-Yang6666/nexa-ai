package com.nexa.application.telegram.command;

import java.util.Map;

/**
 * Telegram 绑定命令（应用层入参，F-1052/F-1054）。
 *
 * <p>在 {@link TelegramLoginCommand} 基础上多一个会话用户 id（{@code bindUserId}）——绑定是
 * 「已登录用户把当前 Telegram 账号绑到本账号」，故归属用户由<b>认证主体</b>提供（接口层从
 * {@code @CurrentActor} 注入），<b>不</b>从请求参数读，防伪造他人归属。</p>
 *
 * @param params     Login Widget 回传的全部查询参数（用于 HMAC 校验 + 取 telegram_id）
 * @param bindUserId 发起绑定的会话用户 id（认证主体，须 &gt; 0）
 */
public record TelegramBindCommand(Map<String, String> params, long bindUserId) {
}
