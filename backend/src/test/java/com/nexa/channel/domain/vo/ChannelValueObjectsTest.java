package com.nexa.channel.domain.vo;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道值对象单测（纯 JUnit，零 Spring）：{@link ChannelStatus} / {@link ChannelType} /
 * {@link MultiKeyMode} / {@link Pagination} / {@link ChannelInfo}。
 *
 * <p>覆盖状态码解析、异步/Ollama 类型判定、轮询模式宽容解析、分页归一、多 Key 信息归一
 * （F-2016~F-2027，backend-engineer §3.3 正常/边界/异常）。</p>
 */
@DisplayName("渠道值对象")
class ChannelValueObjectsTest {

    // ---- ChannelStatus ----

    @Test
    @DisplayName("ChannelStatus.fromCode：合法码解析；未知码 → 400")
    void channelStatusFromCode() {
        assertEquals(ChannelStatus.ENABLED, ChannelStatus.fromCode(1));
        assertEquals(ChannelStatus.MANUALLY_DISABLED, ChannelStatus.fromCode(2));
        assertEquals(ChannelStatus.AUTO_DISABLED, ChannelStatus.fromCode(3));
        assertTrue(ChannelStatus.ENABLED.isEnabled());
        assertFalse(ChannelStatus.MANUALLY_DISABLED.isEnabled());
        assertThrows(InvalidChannelParameterException.class, () -> ChannelStatus.fromCode(99));
    }

    // ---- ChannelType ----

    @Test
    @DisplayName("ChannelType：Ollama(41) 判定；异步类型集合判定")
    void channelType() {
        assertTrue(new ChannelType(ChannelType.OLLAMA).isOllama());
        assertFalse(new ChannelType(1).isOllama());
        // 七类异步：35/36/47/48/49/50
        assertTrue(new ChannelType(35).isAsync());
        assertTrue(new ChannelType(50).isAsync());
        assertFalse(new ChannelType(1).isAsync());
        assertFalse(new ChannelType(ChannelType.OLLAMA).isAsync());
    }

    // ---- MultiKeyMode ----

    @Test
    @DisplayName("MultiKeyMode.fromWire：大小写不敏感；null/未知 → RANDOM")
    void multiKeyMode() {
        assertEquals(MultiKeyMode.RANDOM, MultiKeyMode.fromWire("random"));
        assertEquals(MultiKeyMode.POLLING, MultiKeyMode.fromWire("POLLING"));
        assertEquals(MultiKeyMode.RANDOM, MultiKeyMode.fromWire(null));
        assertEquals(MultiKeyMode.RANDOM, MultiKeyMode.fromWire("garbage"));
        assertEquals("polling", MultiKeyMode.POLLING.wire());
    }

    // ---- Pagination ----

    @Test
    @DisplayName("Pagination.of：缺省/越界归一；offset 推导")
    void pagination() {
        Pagination def = Pagination.of(null, null);
        assertEquals(1, def.page());
        assertEquals(10, def.pageSize());
        assertEquals(0, def.offset());

        Pagination clamped = Pagination.of(0, 500);
        assertEquals(1, clamped.page(), "非正页号归 1");
        assertEquals(100, clamped.pageSize(), "超上限归 100");

        Pagination p3 = Pagination.of(3, 20);
        assertEquals(40, p3.offset(), "offset = (page-1)*size");
    }

    // ---- ChannelInfo ----

    @Test
    @DisplayName("ChannelInfo：负值归一 0；null mode 归 RANDOM；single() 缺省")
    void channelInfo() {
        ChannelInfo info = new ChannelInfo(true, -2, null, -5);
        assertEquals(0, info.multiKeySize());
        assertEquals(0, info.pollingIndex());
        assertEquals(MultiKeyMode.RANDOM, info.mode());

        ChannelInfo single = ChannelInfo.single();
        assertFalse(single.multiKey());
        assertEquals(0, single.multiKeySize());
    }
}
