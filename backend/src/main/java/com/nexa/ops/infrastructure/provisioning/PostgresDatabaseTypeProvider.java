package com.nexa.ops.infrastructure.provisioning;

import com.nexa.ops.application.port.DatabaseTypeProvider;
import org.springframework.stereotype.Component;

/**
 * {@link DatabaseTypeProvider} 的 PostgreSQL 实现（基础设施层，F-4015 database_type）。
 *
 * <p>本工程数据源固定为 PostgreSQL（jdbc:postgresql://.../nexa），故恒返回 {@code "postgres"}。
 * 通过端口抽象避免应用层硬编码，未来多 DB 适配只换实现（端口不变，DDD §2.3）。</p>
 */
@Component
public class PostgresDatabaseTypeProvider implements DatabaseTypeProvider {

    /** {@inheritDoc} */
    @Override
    public String databaseType() {
        return "postgres";
    }
}
