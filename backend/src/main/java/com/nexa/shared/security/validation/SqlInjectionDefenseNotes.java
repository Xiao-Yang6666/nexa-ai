package com.nexa.shared.security.validation;

/**
 * 防 SQL 注入设计说明（SECURITY-NOTES，本切片「防 SQL 注入」功能的成文依据）。
 *
 * <p>本类无运行期行为，仅作为防注入策略的<b>单一可追溯出处</b>（backend-engineer §3.1 领域规则注释标出处），
 * 供后续 bounded context 实现查询时遵循。SQL 注入在本系统的防线分两类：</p>
 *
 * <h2>1. 业务数据值 → JPA 参数化（天然免疫，默认且唯一允许的方式）</h2>
 * <ul>
 *   <li>所有按值过滤一律走 Spring Data JPA 派生查询（方法名）或 {@code @Query} 命名/位置参数
 *       （{@code :param} / {@code ?1}），由 JDBC PreparedStatement 绑定，值与 SQL 文本分离，
 *       拼接式注入从根上不成立。</li>
 *   <li><b>禁止</b>用字符串拼接构造含外部值的 JPQL/原生 SQL（如 {@code "... where name = '" + name + "'"}）。
 *       Repository 实现里出现字符串拼 SQL = code review 直接打回。</li>
 *   <li>{@code LIKE} 模糊查询的通配符（{@code % _}）由参数值携带或在应用侧转义，不拼进模板。</li>
 * </ul>
 *
 * <h2>2. 结构性标识符（列名/排序方向）→ 白名单 + 字符约束（{@link SafeIdentifier}）</h2>
 * <ul>
 *   <li>JDBC 参数只能绑<b>值</b>不能绑<b>标识符</b>，故动态 {@code ORDER BY col DIR} 这类把前端
 *       {@code sort}/{@code order} 拼进 SQL 结构的场景无法参数化——这是注入最易被忽略的入口。</li>
 *   <li>对策：经 {@link SafeIdentifier#of(String, java.util.Set)} 双保险——① 仅接受该查询<b>白名单</b>内的
 *       标识符；② 即便在白名单内也强制只含 {@code [A-Za-z0-9_]}，杜绝引号/分号/注释符/空白。</li>
 *   <li>分页 {@code limit}/{@code offset} 用强类型 {@code int}/{@code long}（接口层 Bean Validation 限范围），
 *       不以字符串拼接。</li>
 * </ul>
 *
 * <h2>3. 输入侧纵深防御（{@link com.nexa.shared.security.validation.SafeText}）</h2>
 * <ul>
 *   <li>接口层 DTO 通用安全约束 {@code @SafeText} 拒绝 NUL/控制字符，挡住截断攻击与日志/终端注入，
 *       作为参数化之外的额外一层（不替代参数化）。</li>
 * </ul>
 *
 * <p>结论：本系统 SQL 注入防护的<b>主防线是 JPA 参数化</b>（自动、默认），{@link SafeIdentifier} 与
 * {@code @SafeText} 是覆盖参数化盲区（标识符）与输入卫生的辅助层。三者叠加构成纵深防御。</p>
 */
public final class SqlInjectionDefenseNotes {

    private SqlInjectionDefenseNotes() {
        // 纯文档类，不实例化。
    }
}
