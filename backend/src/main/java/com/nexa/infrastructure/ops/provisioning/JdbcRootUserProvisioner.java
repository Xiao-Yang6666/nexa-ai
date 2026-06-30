package com.nexa.infrastructure.ops.provisioning;

import com.nexa.application.ops.port.RootUserProvisioner;
import com.nexa.shared.security.rbac.ActorRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link RootUserProvisioner} 的 JDBC/JPA 适配实现（基础设施层，F-4016 创建 root）。
 *
 * <p>系统初始化是一次性引导动作，跨越账号 bounded context 的持久化但不涉及账号域聚合的复杂
 * 业务行为，故 ops 不依赖 account 域聚合，直接用 {@link EntityManager} 原生 SQL 写 {@code users}
 * 表（与 V1/V2 列对齐），避免编译期跨 BC 耦合（backend-engineer §2.5 模块化单体内 BC 解耦）。</p>
 *
 * <p>安全：密码用 BCrypt 哈希后落库，明文绝不入库/入日志（§3.4）。root 角色编码取
 * {@link ActorRole#ROOT}（=100），初始额度取 {@link RootUserProvisioner#ROOT_INITIAL_QUOTA}。</p>
 */
@Component
public class JdbcRootUserProvisioner implements RootUserProvisioner {

    @PersistenceContext
    private EntityManager entityManager;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** {@inheritDoc} */
    @Override
    public boolean rootUserExists() {
        // 统计未软删的 root 用户（role=100）。deleted_at IS NULL 与全表软删惯例一致。
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM users WHERE role = :role AND deleted_at IS NULL")
                .setParameter("role", ActorRole.ROOT.code())
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void provisionRootUser(String username, String rawPassword) {
        try {
            String hashed = passwordEncoder.encode(rawPassword);
            long now = Instant.now().getEpochSecond();
            // aff_code 有全局唯一索引，给一个随机短码避免与未来用户冲突（root 自身邀请码无业务意义但需唯一非冲突）。
            String affCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            entityManager.createNativeQuery("""
                            INSERT INTO users (username, password, display_name, role, status, quota, aff_code, created_at)
                            VALUES (:username, :password, :displayName, :role, 1, :quota, :affCode, :createdAt)
                            """)
                    .setParameter("username", username)
                    .setParameter("password", hashed)
                    .setParameter("displayName", username)
                    .setParameter("role", ActorRole.ROOT.code())
                    .setParameter("quota", ROOT_INITIAL_QUOTA)
                    .setParameter("affCode", affCode)
                    .setParameter("createdAt", now)
                    .executeUpdate();
        } catch (RuntimeException e) {
            // 不吞错：wrap 带上下文（不回显密码），便于定位初始化建 root 失败原因。
            throw new IllegalStateException("provision root user failed for username=" + username, e);
        }
    }
}
