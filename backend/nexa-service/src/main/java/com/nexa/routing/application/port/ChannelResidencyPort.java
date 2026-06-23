package com.nexa.routing.application.port;

import com.nexa.channel.domain.model.Channel;
import com.nexa.compliance.domain.vo.DataResidency;

import java.util.Optional;

/**
 * 渠道数据驻地解析端口（应用层定义，基础设施层适配，F-5018 合规分组选渠护栏依赖）。
 *
 * <p>把「某渠道的请求转发地区属境内还是境外」这一判定数据的<b>来源</b>从选渠主干解耦出来：
 * {@link com.nexa.routing.application.SelectRelayChannelUseCase} 选出渠道后经本端口取其
 * {@link DataResidency}，再交 {@code ComplianceGroupPolicy} 判定合规分组可否命中（F-5018）。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §14.5 F-5018「合规分组限定仅命中境内数据驻地渠道」。
 * 渠道驻地数据当前在 channels 表/Channel 聚合/配置中<b>尚无落地字段</b>（S12 复盘：compliance 子域
 * 已建好但选渠主干零引用）。本端口即「驻地数据源」的接缝：</p>
 * <ul>
 *   <li>返回 {@link Optional#empty()} 表示该渠道驻地<b>未知/未配置</b>——合规分组对未知驻地按
 *       <b>fail-closed</b> 处理（不可证明境内即不放行，杜绝合规请求出境），由用例层落地；</li>
 *   <li>运营在渠道侧补齐驻地配置后，由生产适配器据其映射出 {@link DataResidency} 放行境内渠道。</li>
 * </ul>
 *
 * <p>DDD 端口：应用层声明所需能力，具体实现/数据源在基础设施层（依赖倒置，backend-engineer §2.3）。</p>
 */
public interface ChannelResidencyPort {

    /**
     * 解析给定渠道的数据驻地（请求转发地区境内/境外标注）。
     *
     * @param channel 选中的渠道聚合（非空）
     * @return 该渠道的数据驻地；驻地未知/未配置时返回 {@link Optional#empty()}
     */
    Optional<DataResidency> residencyOf(Channel channel);
}
