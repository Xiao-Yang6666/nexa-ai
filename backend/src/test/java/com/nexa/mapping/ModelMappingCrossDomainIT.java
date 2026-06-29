package com.nexa.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.service.TwoLayerModelResolver;
import com.nexa.relay.domain.vo.AliasScope;
import com.nexa.relay.domain.vo.ModelResolution;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型别名「用户写 = 转发读」跨域端到端集成测试（连真 PostgreSQL）。
 *
 * <p>本测试证明 L1 客户层别名闭环：用户经 <b>model 域</b> REST 端点写入 C→A 别名后，转发引擎实际依赖的
 * <b>relay 域</b> 仓储 + {@link TwoLayerModelResolver} 能读到同一条记录并解析出目标——即「配了即生效」。
 *
 * <p>L1 两域 JPA 实体共用同一张物理表（V13 {@code user_model_aliases}），仅 Hibernate 逻辑实体名不同。
 * 本测试是这一「单表共享」契约的回归护栏。
 *
 * <p><b>L2 全局底仓映射（A→B）已废弃</b>：A→B 下沉为渠道级（{@code Channel.modelMapping}，由
 * {@code RelayForwardUseCase} 选渠后解析），全局 {@code platform_model_mappings} 表已删（V30），
 * 故 {@link TwoLayerModelResolver} 的 L2 lookup 恒等返回 {@code null}（A→B 由渠道级映射在选渠后接管）。
 *
 * <p>环境依赖：{@code @SpringBootTest} 起全栈连真 PG（{@code ddl-auto=validate} + Flyway 建表）。
 * 若 CI/本地无法连 PG，本类在上下文启动阶段失败——按既有惯例可 {@code mvn test -Dtest='!*IT'} 排除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("模型别名跨域闭环 IT - 用户写 model 域端点 = 转发读 relay 域仓储（L1 C→A）")
class ModelMappingCrossDomainIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 转发引擎实际读取的 relay 域 L1 仓储（与 model 域写入仓储共用 user_model_aliases 表）。 */
    @Autowired
    private UserModelAliasRepository relayL1Repo;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String aliasName;
    private long userId;

    @AfterEach
    void cleanUp() {
        if (aliasName != null) {
            jdbcTemplate.update("DELETE FROM user_model_aliases WHERE alias = ?", aliasName);
        }
    }

    @Test
    @DisplayName("用户 POST /api/user/self/model_aliases 创建 C→A，relay 域仓储 + TwoLayerModelResolver 能解析出 A")
    void userWritesL1Alias_relayResolverReadsTarget() throws Exception {
        userId = 900002L;
        aliasName = "c-" + UUID.randomUUID().toString().substring(0, 8);
        String target = "a-" + UUID.randomUUID().toString().substring(0, 8);

        // --- 用户经 model 域接口写入 C→A（self-scope，scope_id 由服务端强制取 userId）---
        String userJwt = mintJwt(userId, "it-user", 1); // role=1 → common
        String body = """
                {"scope_type":"user","alias":"%s","target":"%s"}
                """.formatted(aliasName, target);

        mockMvc.perform(post("/api/user/self/model_aliases")
                        .header("Authorization", "Bearer " + userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.alias", is(aliasName)))
                .andExpect(jsonPath("$.data.target", is(target)));

        // --- 转发读：relay 域 L1 仓储按 user-scope 命中同一条 ---
        assertEquals(target, relayL1Repo.findTargetByAlias(AliasScope.user(userId), aliasName).orElse(null),
                "relay 域仓储应读到用户经 model 域端点写入的 C→A 别名（单表共享）");

        // --- 转发读：TwoLayerModelResolver 解析 C → A（L2 已下沉渠道级，此处恒等 null，A 即解析终点）---
        ModelResolution resolution = TwoLayerModelResolver.resolve(
                aliasName,
                alias -> relayL1Repo.findTargetByAlias(AliasScope.user(userId), alias).orElse(null),
                pub -> null);
        assertEquals(target, resolution.resolvedPublic(), "TwoLayerModelResolver 的 L1 应解析出用户配置的 A");
        assertTrue(resolution.l1Applied(), "L1 层应命中");
    }

    /** 用测试 profile 的 jwt.secret 现签一枚 JWT（sub=userId, role=角色编码, username）。 */
    private String mintJwt(long uid, String username, int roleCode) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(String.valueOf(uid))
                .claim("role", roleCode)
                .claim("username", username)
                .signWith(key)
                .compact();
    }
}
