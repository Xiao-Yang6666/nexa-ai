package com.nexa.domain.deployment.vo;

/**
 * GPU 数值对象（每副本 GPU 数，F-3051）。
 *
 * <p>不可变、按值相等。守护「可用副本查询」的 {@code gpu_count} 入参规则
 * （API-ENDPOINTS §10.3 F-3051）：<b>非正数回退 1</b>（而非报错）——这是契约定义的<b>宽松归一</b>
 * 语义，区别于 hardware_id 的「严格拒绝」。规则固化在工厂方法，调用方无需各自判 &lt;=0。</p>
 *
 * @param value GPU 数（保证 &gt;= 1）
 */
public record GpuCount(int value) {

    /** GPU 数缺省/回退值（契约：gpu_count 非正→回退 1）。 */
    public static final int DEFAULT = 1;

    /**
     * 从可选入参归一 GPU 数：null 或非正一律回退 {@link #DEFAULT}。
     *
     * <p>领域规则来源：API-ENDPOINTS §10.3 F-3051「gpu_count 非正→回退 1」。这是上游容忍式
     * 默认而非校验失败，故不抛异常。</p>
     *
     * @param raw 入参 GPU 数（可空）
     * @return 归一后的 GPU 数（&gt;= 1）
     */
    public static GpuCount ofOrDefault(Integer raw) {
        if (raw == null || raw <= 0) {
            return new GpuCount(DEFAULT);
        }
        return new GpuCount(raw);
    }
}
