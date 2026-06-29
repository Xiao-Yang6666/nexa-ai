package com.nexa.domain.relay.protocol;

import com.nexa.domain.relay.vo.ProtocolFormat;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 协议适配器注册表（包级单例，进程启动注册，COMPAT-LAYER-ARCHITECTURE §2.4）。
 *
 * <p>领域规则：注册表 {@link #get(ProtocolFormat)} 命中才走 compat 壳，未命中回落现有 per-channel 直转
 * （不阻断现网，RL-6 cp_legacy）。加新协议 = 实现 {@link ProtocolAdapter} + 调 {@link #register}。</p>
 *
 * <p>线程安全（{@link ConcurrentHashMap} 支撑），适配器实例无状态可多线程共享。</p>
 */
public final class ProtocolRegistry {

    private static final ConcurrentMap<ProtocolFormat, ProtocolAdapter> REGISTRY = new ConcurrentHashMap<>();

    private ProtocolRegistry() {
    }

    /** 注册协议适配器（通常在 Spring Bean 初始化 / 静态块调用）。 */
    public static void register(ProtocolAdapter adapter) {
        if (adapter == null) return;
        REGISTRY.put(adapter.format(), adapter);
    }

    /** 按协议格式获取适配器；未注册返回 empty（回落 per-channel 直转）。 */
    public static Optional<ProtocolAdapter> get(ProtocolFormat format) {
        return Optional.ofNullable(REGISTRY.get(format));
    }

    /** 注册表是否已包含该协议。 */
    public static boolean contains(ProtocolFormat format) {
        return REGISTRY.containsKey(format);
    }

    /** 仅供测试用：清空注册表。 */
    public static void clearForTest() {
        REGISTRY.clear();
    }
}
