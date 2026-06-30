package com.nexa.application.account.command;

/**
 * WeChat 登录/绑定命令（接口层翻译后的入参，F-1022）。
 *
 * <p>对齐 openapi {@code POST /api/oauth/wechat/bind}（body 仅 {@code code}）。{@code bindUserId} 非空表示
 * 「绑定」语义（已登录用户把微信绑到本账号，AC-5 §3 W7）；为空表示「登录/注册」语义（未登录走找绑定或建号，W8/W9）。
 * 本切片会话层尚未接入，{@code bindUserId} 由后续 wave 从认证主体填入（当前默认 null=登录/注册）。</p>
 *
 * @param code       微信扫码授权后的授权码
 * @param bindUserId 绑定目标用户 id；{@code null}=登录/注册流程，非空=绑定流程
 */
public record WeChatLoginCommand(String code, Long bindUserId) {
}
