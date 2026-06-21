package com.nexa.channel.domain.model;

import com.nexa.channel.domain.exception.ChannelOperationNotSupportedException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.channel.domain.vo.ChannelType;
import com.nexa.channel.domain.vo.MultiKeyMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Channel} 聚合根单测（纯 JUnit，零 Spring/DB）。
 *
 * <p>覆盖渠道域核心领域规则（F-2016~F-2028），按正常/边界/异常组织（backend-engineer §3.3）：
 * 创建不变量（type/key/models 必填、默认启用、models 去重归一）、覆盖式编辑 + key 可选保留、
 * 启停状态迁移、余额更新、上游模型覆盖式应用、异步/Ollama 护栏。</p>
 */
@DisplayName("Channel 聚合根")
class ChannelTest {

    private static final int OLLAMA_TYPE = ChannelType.OLLAMA;
    private static final int OPENAI_TYPE = 1;     // 通用同步渠道
    private static final int MIDJOURNEY_TYPE = 35; // 异步渠道（不可同步测）

    // ---- create：必填 + 默认值 + 归一 ----

    @Test
    @DisplayName("create：type/key/models 合法 → 默认启用、balance/usedQuota=0、group 缺省 default")
    void createNormal() {
        Channel c = Channel.create(OPENAI_TYPE, "sk-xxx", "gpt-4o,gpt-4o-mini", "ch1", null,
                null, null, null, null, null, null, null, null, null);

        assertNull(c.id(), "未持久化 id 为 null");
        assertEquals(OPENAI_TYPE, c.type().code());
        assertEquals("sk-xxx", c.key());
        assertEquals(ChannelStatus.ENABLED, c.status(), "创建默认启用");
        assertEquals(0, BigDecimal.ZERO.compareTo(c.balance()));
        assertEquals(0L, c.usedQuota());
        assertEquals("default", c.group(), "group 缺省 default");
        assertEquals(1, c.autoBan(), "auto_ban 缺省 1");
        assertEquals("gpt-4o,gpt-4o-mini", c.models());
    }

    @Test
    @DisplayName("create：models 去重 + 去空白 + 保序归一")
    void createModelsNormalized() {
        Channel c = Channel.create(OPENAI_TYPE, "k", " a , b ,a, , c ", null, null,
                null, null, null, null, null, null, null, null, null);
        assertEquals("a,b,c", c.models());
    }

    @Test
    @DisplayName("create：type 缺失 → 400 文案")
    void createMissingType() {
        InvalidChannelParameterException e = assertThrows(InvalidChannelParameterException.class,
                () -> Channel.create(null, "k", "m", null, null, null, null, null, null, null, null, null, null, null));
        assertEquals("type is required", e.getMessage());
    }

    @Test
    @DisplayName("create：key 缺失/空白 → 400")
    void createMissingKey() {
        assertThrows(InvalidChannelParameterException.class,
                () -> Channel.create(OPENAI_TYPE, "  ", "m", null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("create：models 缺失/全空白 → 400")
    void createMissingModels() {
        assertThrows(InvalidChannelParameterException.class,
                () -> Channel.create(OPENAI_TYPE, "k", " , ,", null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("create：status_code_mapping 超 1024 → 400")
    void createStatusCodeMappingTooLong() {
        String tooLong = "x".repeat(1025);
        assertThrows(InvalidChannelParameterException.class,
                () -> Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, tooLong, null, null, null));
    }

    @Test
    @DisplayName("create：负权重/负优先级归一为 0；channel_info 缺省单 Key")
    void createNonNegativeNormalized() {
        Channel c = Channel.create(OPENAI_TYPE, "k", "m", null, null, -5L, -3, null, null, null, null, null, null, null);
        assertEquals(0, c.weight());
        assertEquals(0L, c.priority());
        assertEquals(false, c.channelInfo().multiKey());
    }

    // ---- update：覆盖式 + key 可选保留 ----

    @Test
    @DisplayName("update：newKey 为 null → 保留原 key；其余字段覆盖")
    void updateKeepsKeyWhenNull() {
        Channel c = Channel.create(OPENAI_TYPE, "orig-key", "m1", "old", "g1",
                1L, 1, 1, "http://a", null, null, "t1", null, null);
        c.update(OPENAI_TYPE, null, "m2", "new", "g2", 5L, 9, 0, "http://b",
                null, null, "t2", null, new ChannelInfo(true, 3, MultiKeyMode.POLLING, 1));

        assertEquals("orig-key", c.key(), "null newKey 保留原 key");
        assertEquals("m2", c.models());
        assertEquals("new", c.name());
        assertEquals("g2", c.group());
        assertEquals(5L, c.priority());
        assertEquals(9, c.weight());
        assertEquals(0, c.autoBan());
        assertEquals("t2", c.tag());
        assertTrue(c.channelInfo().multiKey());
        assertEquals(MultiKeyMode.POLLING, c.channelInfo().mode());
    }

    @Test
    @DisplayName("update：newKey 非空 → 替换 key")
    void updateReplacesKeyWhenPresent() {
        Channel c = Channel.create(OPENAI_TYPE, "orig", "m", null, null, null, null, null, null, null, null, null, null, null);
        c.update(OPENAI_TYPE, "new-key", "m", null, null, null, null, null, null, null, null, null, null, null);
        assertEquals("new-key", c.key());
    }

    // ---- 状态迁移：启停 ----

    @Test
    @DisplayName("enable/disable：状态迁移正确且幂等")
    void enableDisable() {
        Channel c = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        c.disable();
        assertEquals(ChannelStatus.MANUALLY_DISABLED, c.status());
        c.disable(); // 幂等
        assertEquals(ChannelStatus.MANUALLY_DISABLED, c.status());
        c.enable();
        assertEquals(ChannelStatus.ENABLED, c.status());
    }

    // ---- 余额 ----

    @Test
    @DisplayName("updateBalance：正常更新；null → 400")
    void updateBalance() {
        Channel c = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        c.updateBalance(new BigDecimal("12.50"));
        assertEquals(0, new BigDecimal("12.50").compareTo(c.balance()));
        assertThrows(InvalidChannelParameterException.class, () -> c.updateBalance(null));
    }

    // ---- 测试结果回写 ----

    @Test
    @DisplayName("recordTestResult：写 response_time/test_time，负耗时归一 0")
    void recordTestResult() {
        Channel c = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        Instant t = Instant.ofEpochSecond(1_700_000_000L);
        c.recordTestResult(-1, t);
        assertEquals(0, c.responseTime());
        assertEquals(1_700_000_000L, c.testTime());
    }

    // ---- 上游模型覆盖式应用 ----

    @Test
    @DisplayName("applyUpstreamModels：覆盖式 + 去重保序；空集合 → 400")
    void applyUpstreamModels() {
        Channel c = Channel.create(OPENAI_TYPE, "k", "old", null, null, null, null, null, null, null, null, null, null, null);
        c.applyUpstreamModels(List.of("x", "y", "x", " z "));
        assertEquals("x,y,z", c.models());
        assertThrows(InvalidChannelParameterException.class, () -> c.applyUpstreamModels(List.of()));
        assertThrows(InvalidChannelParameterException.class, () -> c.applyUpstreamModels(List.of("  ", "")));
    }

    // ---- 护栏：异步渠道 / Ollama ----

    @Test
    @DisplayName("ensureSyncTestable：同步渠道通过，异步渠道 → 400")
    void ensureSyncTestable() {
        Channel sync = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        sync.ensureSyncTestable(); // 不抛
        Channel async = Channel.create(MIDJOURNEY_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(ChannelOperationNotSupportedException.class, async::ensureSyncTestable);
    }

    @Test
    @DisplayName("ensureOllama：Ollama 渠道通过，非 Ollama → 400")
    void ensureOllama() {
        Channel ollama = Channel.create(OLLAMA_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        ollama.ensureOllama(); // 不抛
        Channel other = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(ChannelOperationNotSupportedException.class, other::ensureOllama);
    }

    // ---- hasTag ----

    @Test
    @DisplayName("hasTag：匹配/不匹配/null tag")
    void hasTag() {
        Channel tagged = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, "vip", null, null);
        assertTrue(tagged.hasTag("vip"));
        assertEquals(false, tagged.hasTag("other"));
        Channel untagged = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(false, untagged.hasTag("vip"));
    }

    // ---- assignId ----

    @Test
    @DisplayName("assignId：回填主键")
    void assignId() {
        Channel c = Channel.create(OPENAI_TYPE, "k", "m", null, null, null, null, null, null, null, null, null, null, null);
        c.assignId(99L);
        assertEquals(99L, c.id());
    }

    // ---- rehydrate：重建不触发创建校验 ----

    @Test
    @DisplayName("rehydrate：从已存数据装配，null 余额归一 0、null channelInfo 归单 Key")
    void rehydrate() {
        Channel c = Channel.rehydrate(7L, OPENAI_TYPE, "k", 2, "n", 3, null, null, null,
                4L, 1, null, 100L, 50, 1_700_000_000L, null, null, "tg", null, null, 1_699_000_000L);
        assertEquals(7L, c.id());
        assertEquals(ChannelStatus.MANUALLY_DISABLED, c.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.balance()));
        assertSame(false, c.channelInfo().multiKey());
        assertEquals("default", c.group());
    }
}
