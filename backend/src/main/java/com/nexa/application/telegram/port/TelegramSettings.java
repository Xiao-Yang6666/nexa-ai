package com.nexa.application.telegram.port;

/**
 * Telegram 登录设置端口（应用层依赖，基础设施/配置层实现）。
 *
 * <p>承载 Telegram 登录所需的机密与策略：Bot Token（HMAC 校验密钥来源）、是否启用 Telegram 登录、
 * 授权时效窗口（防重放）。定义为端口而非直接读 Spring 配置，是为了让用例可在单测中注入桩值
 * （固定 token / 固定窗口）而无需起 Spring 上下文（backend-engineer §3.4 配置外置）。</p>
 *
 * <p><b>安全铁律</b>：Bot Token 是机密，仅在服务端用于派生 HMAC 密钥，<b>绝不</b>下发给前端、绝不入日志、
 * 绝不进客户视图（{@code StatusAggregateView.telegram_oauth} 只暴露「是否启用」布尔，不含 token）。</p>
 */
public interface TelegramSettings {

    /**
     * 是否启用 Telegram 登录（关闭时登录/绑定用例直接拒绝）。
     *
     * @return 启用返回 {@code true}
     */
    boolean isTelegramLoginEnabled();

    /**
     * Telegram Bot Token（HMAC 校验密钥来源）。
     *
     * @return Bot Token；未配置时返回 {@code null}/空（校验时按「未配置」失败）
     */
    String botToken();

    /**
     * 授权数据有效期窗口（秒）。
     *
     * <p>防重放：Login Widget 回传的 {@code auth_date} 与服务端当前时间相差超过本窗口即视为过期拒绝。
     * {@code <= 0} 表示不做时效校验（仅校验 HMAC 真伪）。Telegram 官方建议 86400 秒内。</p>
     *
     * @return 有效期秒数
     */
    long authValiditySeconds();
}
