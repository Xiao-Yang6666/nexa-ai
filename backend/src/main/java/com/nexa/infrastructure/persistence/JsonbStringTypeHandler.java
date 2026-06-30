package com.nexa.infrastructure.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL {@code jsonb} 列 ↔ Java {@code String} 的 MyBatis TypeHandler（基础设施层共享构件）。
 *
 * <p>背景：迁移前由 Hibernate 6 的 {@code @JdbcTypeCode(SqlTypes.JSON)} 自动完成 {@code String ↔ jsonb}
 * 互转；MyBatis-Plus 默认把 String 以 {@code varchar} 绑定，PG 会抛
 * {@code "column is of type jsonb but expression is of type character varying"}。本 Handler 把写入参数
 * 以 {@link Types#OTHER} 绑定（PG JDBC 驱动据此发送 unknown 类型，由 PG 隐式转换为 jsonb），读取时按
 * 普通字符串取出——行为等价迁移前以 String 承载 jsonb 的语义。</p>
 *
 * <p>用法：在承载 jsonb 的 PO 字段上 {@code @TableField(value = "<col>", typeHandler = JsonbStringTypeHandler.class)}，
 * 并在 PO 类上加 {@code @TableName(value = "<table>", autoResultMap = true)} 使读取也走本 Handler。
 * 不引入 {@code org.postgresql.util.PGobject}（postgresql 驱动为 runtime scope，编译期不可见），
 * 改用 {@code setObject(i, value, Types.OTHER)} 达到等价效果。</p>
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        // 以 OTHER 绑定：PG 驱动发送 unknown 类型，由 PG 隐式转为 jsonb（等价 Hibernate JSON 写入）。
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
