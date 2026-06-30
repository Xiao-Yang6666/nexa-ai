package com.nexa.application.telegram.command;

import java.util.Map;

/**
 * Telegram 登录命令（应用层入参，F-1051）。
 *
 * <p>承载 Login Widget 回传的全部查询参数（含 id/hash/auth_date 及可选 first_name/username/photo_url）。
 * 用「全部参数表」而非逐字段，是因为 Telegram HMAC 校验<b>要求对收到的所有非 hash 字段签名</b>，
 * 接口层必须把全部参数透传给用例，由领域 {@code TelegramAuthData.fromParams} 决定哪些参与签名
 * （不能在接口层挑字段，否则攻击者可注入未签名字段绕过校验，F-1053）。</p>
 *
 * @param params Login Widget 回传的全部查询参数（键→值）
 */
public record TelegramLoginCommand(Map<String, String> params) {
}
