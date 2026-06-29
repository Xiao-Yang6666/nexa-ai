package com.nexa.domain.task.vo;

/**
 * 任务平台枚举（对齐 DB-SCHEMA §9 Task.Platform，PRD AT-1）。
 *
 * <p>MJ/Suno/Kling/Jimeng/Vidu/Hailuo/DoubaoVideo 七类异步渠道（PRD relay §6051）。</p>
 */
public enum TaskPlatform {

    MIDJOURNEY,
    SUNO,
    KLING,
    JIMENG,
    VIDU,
    HAILUO,
    DOUBAO_VIDEO,
    /** 兜底未知平台。 */
    UNKNOWN;

    /**
     * 从 wire 格式反序列化（DB 小写，openapi 大写）。
     *
     * @param wire 线上格式
     * @return 枚举值
     */
    public static TaskPlatform fromWire(String wire) {
        if (wire == null) return UNKNOWN;
        try {
            return valueOf(wire.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** @return wire 格式（小写，对齐 DB-SCHEMA varchar(30)）。 */
    public String toWire() {
        return this.name().toLowerCase();
    }
}
