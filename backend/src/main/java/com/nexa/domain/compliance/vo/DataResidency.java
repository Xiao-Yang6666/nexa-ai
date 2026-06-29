package com.nexa.domain.compliance.vo;

import java.util.Objects;

/**
 * 数据驻地（值对象，不可变，按值相等）——F-5019 数据出境告知与驻地标注，DC-008/DC-009。
 *
 * <p>标注一个 provider/渠道把请求转发到的地理区域属境内还是境外，用于：
 * <ul>
 *   <li>价格页 / 控制台公开展示「数据驻地」与「请求转发地区」（公开可见，F-5019 验收）；</li>
 *   <li>合规分组选渠时判定该渠道是否「数据不出境」（F-5018，{@link #isDomestic()}）。</li>
 * </ul>
 * 领域规则来源：API-ENDPOINTS §14.5 F-5019「每 provider 标注境内外驻地并明示请求转发地区」。
 * 驻地标注复用模块四 VendorMeta（{@code com.nexa.domain.model.model.Vendor}）的展示载体，本值对象
 * 承载「境内/境外」的领域判定语义。</p>
 *
 * @param region   人类可读的地区标识（如 {@code "cn-east"} / {@code "us-west"} / {@code "eu"}），用于公开展示
 * @param domestic 是否为境内驻地（数据不出境）
 */
public record DataResidency(String region, boolean domestic) {

    /**
     * 紧凑构造校验：region 非空白。
     *
     * @throws IllegalArgumentException region 为 null/空白
     */
    public DataResidency {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("data residency region must not be null or blank");
        }
        region = region.trim();
    }

    /**
     * 构造境内驻地（数据不出境）。
     *
     * @param region 地区标识
     * @return 境内驻地值对象
     */
    public static DataResidency domestic(String region) {
        return new DataResidency(region, true);
    }

    /**
     * 构造境外驻地（数据出境）。
     *
     * @param region 地区标识
     * @return 境外驻地值对象
     */
    public static DataResidency overseas(String region) {
        return new DataResidency(region, false);
    }

    /**
     * 是否为境内驻地（数据不出境）。
     *
     * <p>合规分组选渠（F-5018）据此放行/剔除候选渠道：合规分组仅允许 {@code true} 的渠道。</p>
     *
     * @return 境内返回 {@code true}
     */
    public boolean isDomestic() {
        return domestic;
    }

    /**
     * 是否涉及数据出境（境外驻地）。
     *
     * <p>价格页/控制台据此对该 provider 标注「数据出境」告知（F-5019 / DC-009 明示义务）。</p>
     *
     * @return 出境返回 {@code true}
     */
    public boolean crossesBorder() {
        return !domestic;
    }

    /** @return 用于公开展示的驻地描述（如 {@code "境内 (cn-east)"} / {@code "境外 (us-west)"}） */
    public String displayLabel() {
        return (domestic ? "境内 (" : "境外 (") + region + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataResidency other)) {
            return false;
        }
        return domestic == other.domestic && region.equals(other.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(region, domestic);
    }
}
