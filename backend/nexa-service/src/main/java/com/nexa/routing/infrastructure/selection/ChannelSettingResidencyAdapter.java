package com.nexa.routing.infrastructure.selection;

import com.nexa.channel.domain.model.Channel;
import com.nexa.compliance.domain.vo.DataResidency;
import com.nexa.routing.application.port.ChannelResidencyPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 渠道数据驻地解析端口的生产实现（基础设施层 adapter，F-5018）。
 *
 * <p>现状（S12 复盘）：channels 表 / {@link Channel} 聚合 / 渠道配置中<b>尚无</b>数据驻地字段，
 * 故本适配器对所有渠道返回 {@link Optional#empty()}（驻地未知）。其语义经
 * {@link com.nexa.routing.application.SelectRelayChannelUseCase} 的合规护栏落地为 <b>fail-closed</b>：
 * 合规分组下「无法证明境内」的渠道一律排除/拒绝，杜绝合规请求被路由出境（F-5018 强约束优先于可用性）。</p>
 *
 * <p>接线点（待运营配置后启用，本片不实现以免大改 Channel 模型/DB）：渠道侧补齐驻地标注后——
 * 例如在 channel setting JSON 增 {@code residency} 键、或新增 channel_residency 映射表/region 字典——
 * 在此把该信号映射为 {@link DataResidency#domestic(String)} / {@link DataResidency#overseas(String)}。
 * 端口签名不变，仅本实现体随数据源补齐，选渠主干与单测均无需改动。</p>
 */
@Component
public class ChannelSettingResidencyAdapter implements ChannelResidencyPort {

    /**
     * {@inheritDoc}
     *
     * <p>驻地数据源待运营配置：当前恒返回 {@link Optional#empty()}（未知驻地）。合规分组据此
     * fail-closed（排除/拒绝），非合规分组不受影响（不读驻地）。</p>
     */
    @Override
    public Optional<DataResidency> residencyOf(Channel channel) {
        return Optional.empty();
    }
}
