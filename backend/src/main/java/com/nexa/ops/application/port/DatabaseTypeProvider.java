package com.nexa.ops.application.port;

/**
 * 数据库类型探针（应用层端口，F-4015 GET /api/setup 出参 {@code database_type}）。
 *
 * <p>初始化状态查询需返回当前数据库类型（mysql/postgres/sqlite）。本工程恒为 postgres，
 * 但通过端口抽象避免在应用层硬编码，便于未来多 DB 适配与测试替身。</p>
 */
public interface DatabaseTypeProvider {

    /**
     * @return 数据库类型字符串（mysql / postgres / sqlite）
     */
    String databaseType();
}
