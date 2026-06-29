package com.nexa.application.ops.performance;

import com.nexa.application.ops.port.SystemRuntimeProbe;
import org.springframework.stereotype.Service;

/**
 * 强制执行 GC 用例（应用层，F-4021 POST /api/performance/gc）。
 *
 * <p>编排：委托运行时探针建议执行 GC。幂等运维动作（重复调用安全）。</p>
 */
@Service
public class ForceGcUseCase {

    private final SystemRuntimeProbe systemRuntimeProbe;

    /**
     * @param systemRuntimeProbe 运行时探针
     */
    public ForceGcUseCase(SystemRuntimeProbe systemRuntimeProbe) {
        this.systemRuntimeProbe = systemRuntimeProbe;
    }

    /** 强制执行一次 GC。 */
    public void execute() {
        systemRuntimeProbe.forceGarbageCollection();
    }
}
