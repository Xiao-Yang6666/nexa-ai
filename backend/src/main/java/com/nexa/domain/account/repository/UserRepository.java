package com.nexa.domain.account.repository;

import com.nexa.domain.account.model.User;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.Username;

import java.util.List;
import java.util.Optional;

/**
 * 用户聚合仓储接口（领域层定义，基础设施层实现）。
 *
 * <p>DDD 依赖倒置：domain 只声明"需要什么持久化能力"，不关心 JPA/SQL 细节（backend-engineer §2.3）。
 * 应用层用例仅依赖本接口，可在单测中用桩/mock 替换，无需起 DB。实现见
 * {@code infrastructure.persistence.UserRepositoryImpl}。</p>
 */
public interface UserRepository {

    /**
     * 判断用户名是否已被占用。
     *
     * <p>用于注册查重（PRD AC-1 R9「判用户名是否已存在」）。</p>
     *
     * @param username 用户名值对象
     * @return 已存在返回 {@code true}
     */
    boolean existsByUsername(Username username);

    /**
     * 按用户名查找用户（用于登录定位账号）。
     *
     * @param username 用户名值对象
     * @return 命中返回用户聚合，否则空
     */
    Optional<User> findByUsername(Username username);

    /**
     * 按主键查找用户。
     *
     * <p>用于 self-scope 端点（本人信息查询 F-1045、个人设置保存 F-1014、改密）凭会话内
     * {@code user_id} 定位本人聚合。</p>
     *
     * @param id 用户主键
     * @return 命中返回用户聚合，否则空
     */
    Optional<User> findById(long id);

    /**
     * 按邮箱查找用户（用于找回密码 AC-3 判邮箱是否已注册 / 令牌归属）。
     *
     * <p>邮箱在 DB-SCHEMA §1 非唯一约束（历史/OAuth 可能产生重复），此处约定返回首个命中；
     * 找回密码以邮箱+令牌双因子定位，邮箱重复的边界由后续 wave 视业务需要收敛。</p>
     *
     * @param email 邮箱值对象
     * @return 命中返回用户聚合，否则空
     */
    Optional<User> findByEmail(Email email);

    /**
     * 持久化用户聚合（新增或更新）。
     *
     * <p>新用户保存后，返回值携带数据库生成的自增 {@code id}（注册场景需要）。</p>
     *
     * @param user 待保存的用户聚合
     * @return 持久化后的用户聚合（含 id）
     */
    User save(User user);

    /**
     * 分页查询用户（管理端列表，F-1008，按 id 升序）。
     *
     * <p>对齐 API-ENDPOINTS §1.4 {@code GET /api/user/}（分页）。{@code keyword} 非空时按
     * username/email/group 模糊匹配（搜索），为空时返回全量分页（列表）。软删除用户由
     * {@code @SQLRestriction} 在实现层过滤，不会返回。</p>
     *
     * @param keyword  搜索关键词，{@code null}/空白表示不过滤（纯列表）
     * @param page     页码（从 1 起）
     * @param pageSize 每页条数（&gt;0）
     * @return 当页用户聚合 + 总数的分页结果
     */
    Page<User> search(String keyword, int page, int pageSize);

    /**
     * 软删除用户（管理端删除动作，F-1010 delete）。
     *
     * <p>对齐 DB-SCHEMA §1 软删除：写入 {@code deleted_at} 时间戳而非物理删除，配合
     * {@code @SQLRestriction("deleted_at IS NULL")} 使该用户后续不再被任何查询返回
     * （AC-10 §6「删除 → DeletedAt 写入，列表不再返回该用户」）。幂等：重复删除无害。</p>
     *
     * @param id 目标用户主键
     */
    void softDelete(long id);

    /**
     * 管理端用户分页结果（领域层定义的轻量分页载体）。
     *
     * @param items    当页用户聚合列表
     * @param total    匹配总条数（用于前端计算总页数）
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      载荷类型
     */
    record Page<T>(List<T> items, long total, int page, int pageSize) {
    }
}
