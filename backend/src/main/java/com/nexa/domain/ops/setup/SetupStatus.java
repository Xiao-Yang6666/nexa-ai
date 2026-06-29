package com.nexa.domain.ops.setup;

/**
 * 系统初始化状态（值对象，对齐 F-4015 GET /api/setup 出参）。
 *
 * <p>描述「系统是否已完成首次部署引导」及未初始化时的引导上下文：是否已有 root 用户、
 * 数据库类型。接口层据此返回 {@code {status:true}} 或
 * {@code {status:false, root_init, database_type}}（API-ENDPOINTS §9.1）。</p>
 *
 * @param initialized  系统是否已初始化（= setups 表存在标记记录）
 * @param rootExists   是否已存在 root 用户（未初始化时引导前端是否跳过建 root）
 * @param databaseType 数据库类型（mysql/postgres/sqlite，本工程恒为 postgres）
 */
public record SetupStatus(boolean initialized, boolean rootExists, String databaseType) {

    /**
     * 已初始化状态（探测到初始化标记）。
     *
     * <p>命名为 {@code completed} 而非 {@code initialized}，避免与 record 自动生成的
     * 组件访问器 {@link #initialized()}（返回 boolean）方法签名冲突。</p>
     *
     * @return 已初始化状态（rootExists 恒 true、databaseType 仅在未初始化时有意义，此处置空）
     */
    public static SetupStatus completed() {
        return new SetupStatus(true, true, null);
    }

    /**
     * 未初始化状态（引导阶段）。
     *
     * @param rootExists   是否已有 root 用户
     * @param databaseType 数据库类型
     * @return 未初始化状态
     */
    public static SetupStatus pending(boolean rootExists, String databaseType) {
        return new SetupStatus(false, rootExists, databaseType);
    }
}
