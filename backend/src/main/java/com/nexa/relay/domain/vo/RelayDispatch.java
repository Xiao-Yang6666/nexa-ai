package com.nexa.relay.domain.vo;

import java.util.Objects;

/**
 * 路径分发判定结果（RL-2 {@code Path2RelayMode} 输出，不可变值对象）。
 *
 * <p>承载请求路径解析出的 {@link RelayMode}（做什么业务）+ {@link ProtocolFormat}（用什么协议），
 * 是 RL-2 → RL-4/RL-6 的衔接载体。</p>
 *
 * @param mode   中继模式
 * @param format 入站协议格式
 */
public record RelayDispatch(RelayMode mode, ProtocolFormat format) {

    public RelayDispatch {
        Objects.requireNonNull(mode, "relay mode must not be null");
        Objects.requireNonNull(format, "protocol format must not be null");
    }
}
