package com.nexa.deployment.domain.model;

/**
 * io.net 集成状态聚合（充血领域模型，F-3039）。
 *
 * <p>承载「io.net 集成设置查询」结果并<b>自派生</b> {@code can_connect}：契约出参
 * {@code { provider:"io.net", enabled, configured, can_connect }}（API-ENDPOINTS §10.1 F-3039）。
 * {@code canConnect} 不是外部传入的裸字段，而是由 {@code enabled && configured} 派生——把
 * 「可连接 = 已启用且已配置 key」这条业务规则固化在领域对象，避免接口层重算导致口径漂移
 * （backend-engineer §2.2 充血模型）。</p>
 *
 * <p>本聚合纯由本地配置（Option {@code model_deployment.ionet.*}）构造，<b>不触发上游调用</b>
 * （契约 F-3039 错误码段：「未启用或 key 缺失时 getIoAPIKey 报错（仅连接类接口触发）」——即设置查询本身
 * 不发上游请求，故 key 缺失不在此报错，只如实反映 configured=false）。</p>
 *
 * @param provider   集成 provider 标识（固定 {@code io.net}）
 * @param enabled    集成总开关（OptionMap[ionet.enabled]==true）
 * @param configured 是否已配置 api_key（非空）
 */
public record IntegrationStatus(String provider, boolean enabled, boolean configured) {

    /** provider 固定值（API-ENDPOINTS §10：provider 固定 io.net）。 */
    public static final String PROVIDER_IONET = "io.net";

    /**
     * 工厂：构造 io.net 集成状态。
     *
     * @param enabled    集成是否启用
     * @param configured api_key 是否已配置
     * @return 集成状态聚合（provider 固定 io.net）
     */
    public static IntegrationStatus ionet(boolean enabled, boolean configured) {
        return new IntegrationStatus(PROVIDER_IONET, enabled, configured);
    }

    /**
     * 是否可连接（派生规则：已启用且已配置 key）。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.1 F-3039「can_connect: &lt;enabled &amp;&amp; configured&gt;」。</p>
     *
     * @return enabled 且 configured 时返回 true
     */
    public boolean canConnect() {
        return enabled && configured;
    }
}
