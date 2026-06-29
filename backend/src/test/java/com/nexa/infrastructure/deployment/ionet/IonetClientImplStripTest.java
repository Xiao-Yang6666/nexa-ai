package com.nexa.infrastructure.deployment.ionet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IonetClientImpl#stripSensitive} 敏感键剔除单测（纯 JUnit，产品铁律）。
 *
 * <p>验证密钥不下发客户端（API-ENDPOINTS §10 铁律）：顶层 + 嵌套 Map/List 中的 api_key 等敏感键
 * 被递归剔除，非敏感字段完整保留，且不修改入参（纯函数）。</p>
 */
@DisplayName("io.net 响应敏感键剔除")
class IonetClientImplStripTest {

    @Test
    @DisplayName("顶层敏感键被剔除，普通字段保留")
    void stripsTopLevel() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("name", "cluster-a");
        src.put("api_key", "sk-secret");
        src.put("token", "t-123");
        src.put("price", 9.9);

        Map<String, Object> safe = IonetClientImpl.stripSensitive(src);

        assertEquals("cluster-a", safe.get("name"));
        assertEquals(9.9, safe.get("price"));
        assertFalse(safe.containsKey("api_key"), "api_key 必须剔除");
        assertFalse(safe.containsKey("token"), "token 必须剔除");
    }

    @Test
    @DisplayName("嵌套 Map / List 中的敏感键递归剔除")
    void stripsNested() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("secret", "xxx");
        nested.put("region", "us");
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("config", nested);
        src.put("containers", List.of(Map.of("container_id", "c1", "password", "p1")));

        Map<String, Object> safe = IonetClientImpl.stripSensitive(src);

        @SuppressWarnings("unchecked")
        Map<String, Object> safeConfig = (Map<String, Object>) safe.get("config");
        assertEquals("us", safeConfig.get("region"));
        assertFalse(safeConfig.containsKey("secret"), "嵌套 secret 必须剔除");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> safeContainers = (List<Map<String, Object>>) safe.get("containers");
        assertEquals("c1", safeContainers.get(0).get("container_id"));
        assertFalse(safeContainers.get(0).containsKey("password"), "列表内 password 必须剔除");
    }

    @Test
    @DisplayName("不修改入参（纯函数）")
    void doesNotMutateSource() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("api_key", "sk-secret");
        src.put("name", "x");

        IonetClientImpl.stripSensitive(src);

        assertTrue(src.containsKey("api_key"), "入参不应被修改");
    }
}
