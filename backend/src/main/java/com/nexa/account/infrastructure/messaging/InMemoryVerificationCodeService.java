package com.nexa.account.infrastructure.messaging;

import com.nexa.account.application.port.VerificationCodeService;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.VerificationCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码服务端口 {@link VerificationCodeService} 的内存桩实现（基础设施层）。
 *
 * <p>承载 F-1004 发码暂存与 F-1005 校验消费。验证码 + 有效期是带状态的副作用资源，
 * 本切片用进程内 {@link ConcurrentHashMap} 暂存，<b>TODO 生产环境换 Redis</b>（带原生 TTL、
 * 跨实例共享、防内存泄漏）。当前实现单实例、进程重启即失效，仅供联调/单测打通流程。</p>
 *
 * <p>领域规则来源：PRD prd-account.md AC-1 R4~R7「请求发送验证码 → 校验匹配且未过期」、
 * API-ENDPOINTS §1.1「EmailVerificationEnabled 时校验」。TTL 取 10 分钟（行业惯例）。
 * 校验通过即消费（一次性失效）防重放；校验失败不消费。</p>
 */
@Component
public class InMemoryVerificationCodeService implements VerificationCodeService {

    /** 验证码有效期：10 分钟（PRD「未过期」语义，生产可经配置调整）。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** email → 已签发验证码 + 过期时刻。TODO 生产换 Redis（原生 TTL + 集群共享）。 */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 内部暂存条目：验证码值 + 过期 epoch 毫秒。 */
    private record Entry(String code, long expiresAtEpochMilli) {
        boolean isExpired(long nowEpochMilli) {
            return nowEpochMilli > expiresAtEpochMilli;
        }
    }

    /** {@inheritDoc} */
    @Override
    public VerificationCode issue(Email email) {
        VerificationCode code = VerificationCode.generate();
        long expiresAt = Instant.now().plus(TTL).toEpochMilli();
        // 同邮箱重复请求覆盖旧码（以最新为准，PRD 约定）。
        store.put(email.value(), new Entry(code.value(), expiresAt));
        return code;
    }

    /** {@inheritDoc} */
    @Override
    public boolean verifyAndConsume(Email email, VerificationCode code) {
        if (code == null) {
            return false;
        }
        String key = email.value();
        Entry entry = store.get(key);
        if (entry == null) {
            return false; // 无暂存（从未发码或已被消费）。
        }
        long now = Instant.now().toEpochMilli();
        if (entry.isExpired(now)) {
            // 过期不算匹配；顺手清理避免内存堆积（生产 Redis 由 TTL 自动回收）。
            store.remove(key, entry);
            return false;
        }
        // 用值对象的常量时间比较，避免时序侧信道泄露前缀匹配长度。
        boolean matched = VerificationCode.of(entry.code()).matches(code);
        if (matched) {
            // 一次性消费：通过即删除暂存，防同码重放（PRD AC-1 R7 + 一次性语义）。
            store.remove(key, entry);
        }
        return matched;
    }
}
