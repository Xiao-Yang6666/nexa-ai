package com.nexa.model.application;

import com.nexa.model.application.port.UserGroupQuery;
import com.nexa.model.domain.exception.AliasCrossScopeException;
import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.exception.UserModelAliasNotFoundException;
import com.nexa.model.domain.model.UserModelAlias;
import com.nexa.model.domain.repository.PublicModelRepository;
import com.nexa.model.domain.repository.UserModelAliasRepository;
import com.nexa.model.domain.vo.AliasScopeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 客户层自助映射 CRUD 用例（应用层，C→A，self-scope 强制，F-6003/F-6004）。
 *
 * <p>承载本人/本组列表、创建、更新、删除、候选联想全部用例。<b>越权护栏在本用例落地</b>
 * （DB-SCHEMA §18 + ROLE-PERMISSION-MATRIX §3）：
 * <ul>
 *   <li>写入 scope_type=user 时，强制 scope_id=:caller_user_id（不信任入参，防越权写他人）。</li>
 *   <li>写入 scope_type=group 时，强制该 group = 调用者所属 group（防写非本组）。</li>
 *   <li>更新/删除按 id 命中后，再断言归属本人 user-scope 或本组 group-scope，否则 403。</li>
 * </ul>
 * </p>
 *
 * <p><b>候选联想（F-6003 候选层 B 不可见闸）</b>：来源 = PublicModel enabled 全集（仅 A，绝不含 B）。
 * <b>target 不校验白名单</b>（COMPAT §2 铁律）：候选只是前端联想提示，落库不拦客户硬输的名。</p>
 */
@Service
public class ManageUserModelAliasUseCase {

    private final UserModelAliasRepository aliasRepository;
    private final PublicModelRepository publicModelRepository;
    private final UserGroupQuery userGroupQuery;

    /**
     * @param aliasRepository       自助映射仓储
     * @param publicModelRepository 对外模型仓储（候选来源）
     * @param userGroupQuery        用户分组查询端口（self-scope group 判定）
     */
    public ManageUserModelAliasUseCase(UserModelAliasRepository aliasRepository,
                                       PublicModelRepository publicModelRepository,
                                       UserGroupQuery userGroupQuery) {
        this.aliasRepository = aliasRepository;
        this.publicModelRepository = publicModelRepository;
        this.userGroupQuery = userGroupQuery;
    }

    /**
     * 列出当前用户可见映射：本人 user-scope + 所属 group-scope 合并（F-6003）。
     *
     * <p>优先级 user&gt;group：同一 alias C 同时存在 user/group 命中时，user 覆盖 group。本方法在合并层
     * 去重——同 alias 优先保留 user-scope 项。</p>
     *
     * @param userId 当前用户 id
     * @return 可见映射列表（user 优先去重后）
     */
    @Transactional(readOnly = true)
    public List<UserModelAlias> listVisible(long userId) {
        List<String> groups = currentGroups(userId);
        List<UserModelAlias> all = aliasRepository.findByUserAndGroups(userId, groups);

        // 优先级 user>group：以 alias 为键，user-scope 项覆盖 group-scope 项（COMPAT §2）。
        java.util.Map<String, UserModelAlias> byAlias = new java.util.LinkedHashMap<>();
        for (UserModelAlias a : all) {
            UserModelAlias prev = byAlias.get(a.alias());
            if (prev == null || (a.scopeType() == AliasScopeType.USER && prev.scopeType() == AliasScopeType.GROUP)) {
                byAlias.put(a.alias(), a);
            }
        }
        return new ArrayList<>(byAlias.values());
    }

    /**
     * 创建 C→A 映射（F-6003，self-scope 强制）。
     *
     * @param userId    当前用户 id（self-scope 强制基准，不信任入参）
     * @param scopeType 作用域类型（user/group）
     * @param alias     客户别名 C
     * @param target    目标公开名 A（不校验白名单）
     * @param enabled   是否启用
     * @return 创建后的映射
     * @throws InvalidModelParameterException 入参非法 / 同作用域 C 已存在
     * @throws AliasCrossScopeException       跨 scope 越权写
     */
    @Transactional
    public UserModelAlias create(long userId, AliasScopeType scopeType, String alias,
                                 String target, Boolean enabled) {
        if (scopeType == null) {
            throw new InvalidModelParameterException("scope_type 不能为空");
        }
        // 越权护栏：scope_id 由服务端按 scope_type 强制推导，绝不取客户入参（防越权写他人/他组）。
        String scopeId = resolveAndAuthorizeScopeId(userId, scopeType);

        UserModelAlias entity = UserModelAlias.create(scopeType, scopeId, alias, target, enabled);
        // 幂等键 (scope_type, scope_id, alias) 同作用域唯一（uk_scope_alias 兜底；先查询友好提示）。
        aliasRepository.findByScopeAndAlias(scopeType, scopeId, entity.alias()).ifPresent(existing -> {
            throw new InvalidModelParameterException("同作用域下别名已存在");
        });
        return aliasRepository.save(entity);
    }

    /**
     * 更新 C→A 映射（F-6003，仅本人 scope）。
     *
     * @param userId  当前用户 id（归属校验）
     * @param id      映射 id
     * @param target  新目标 A（可空 → 不改；不校验白名单）
     * @param enabled 新启用态（可空 → 不改）
     * @return 更新后的映射
     * @throws UserModelAliasNotFoundException 不存在
     * @throws AliasCrossScopeException        非本人/本组 scope（越权）
     */
    @Transactional
    public UserModelAlias update(long userId, long id, String target, Boolean enabled) {
        UserModelAlias entity = aliasRepository.findById(id)
                .orElseThrow(() -> new UserModelAliasNotFoundException(id));
        requireOwnership(userId, entity);
        entity.update(target, enabled);
        return aliasRepository.save(entity);
    }

    /**
     * 软删除 C→A 映射（F-6003，仅本人 scope）。
     *
     * @param userId 当前用户 id（归属校验）
     * @param id     映射 id
     * @throws UserModelAliasNotFoundException 不存在
     * @throws AliasCrossScopeException        非本人/本组 scope（越权）
     */
    @Transactional
    public void delete(long userId, long id) {
        UserModelAlias entity = aliasRepository.findById(id)
                .orElseThrow(() -> new UserModelAliasNotFoundException(id));
        requireOwnership(userId, entity);
        aliasRepository.deleteById(id);
    }

    /**
     * 候选模型联想（F-6003 候选层，公开 A 全集，B 不可见闸）。
     *
     * @param keyword 关键词（可空白 → 全集）
     * @return 上架公开名 A 列表（按 keyword 大小写不敏感包含过滤；绝不含任何 B）
     */
    @Transactional(readOnly = true)
    public List<String> candidates(String keyword) {
        List<String> all = publicModelRepository.findEnabledNames();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty()) {
            return all;
        }
        return all.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(kw)).toList();
    }

    // ---- self-scope 越权护栏（DB-SCHEMA §18 / ROLE-PERMISSION-MATRIX §3） ----

    /**
     * 按 scope_type 推导并授权 scope_id（写入路径越权护栏，绝不取客户入参 scope_id）。
     *
     * @param userId    当前用户 id
     * @param scopeType 作用域类型
     * @return 服务端强制推导的 scope_id（user→userId 字符串化 / group→本人所属 group）
     * @throws AliasCrossScopeException user 无所属 group 却写 group-scope
     */
    private String resolveAndAuthorizeScopeId(long userId, AliasScopeType scopeType) {
        if (scopeType == AliasScopeType.USER) {
            return String.valueOf(userId);
        }
        // group-scope：强制取调用者本人所属 group，禁写他组。
        Optional<String> group = userGroupQuery.groupOf(userId);
        return group.filter(g -> !g.isBlank())
                .orElseThrow(() -> new AliasCrossScopeException("当前用户无所属分组，不能创建分组级映射"));
    }

    /**
     * 断言映射归属当前用户（本人 user-scope 或本组 group-scope），否则越权 403。
     *
     * @param userId 当前用户 id
     * @param entity 目标映射
     * @throws AliasCrossScopeException 非本人/本组
     */
    private void requireOwnership(long userId, UserModelAlias entity) {
        if (entity.scopeType() == AliasScopeType.USER) {
            if (!entity.isOwnedBy(userId)) {
                throw new AliasCrossScopeException("无权操作他人映射");
            }
            return;
        }
        // group-scope：scope_id（分组名）须等于调用者所属 group。
        String myGroup = userGroupQuery.groupOf(userId).orElse(null);
        if (myGroup == null || !myGroup.equals(entity.scopeId())) {
            throw new AliasCrossScopeException("无权操作非本组映射");
        }
    }

    /**
     * 取当前用户所属 group 列表（单组模型，UserGroupQuery 返回单值；无组返回空集）。
     *
     * @param userId 用户 id
     * @return 所属分组名列表（0 或 1 个）
     */
    private List<String> currentGroups(long userId) {
        return userGroupQuery.groupOf(userId)
                .filter(g -> !g.isBlank())
                .map(List::of)
                .orElseGet(List::of);
    }
}
