package com.nexa.application.account;

import com.nexa.domain.account.exception.UserNotFoundException;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询本人账户信息用例（应用层，F-1045 本人信息端点 {@code GET /api/user/self}）。
 *
 * <p>DDD 分层：本类<b>薄</b>，仅按会话内 {@code user_id} 从仓储定位本人聚合并回传，
 * 不含业务规则（backend-engineer §2.1 application 层只编排）。self-scope 由接口层从
 * {@code AuthenticatedActor#userId()} 取目标 id 保证（不接受外部传入 id），本用例不重复鉴权。</p>
 *
 * <p>事务：只读（{@code readOnly}），单次按主键查询，命中失败抛 {@link UserNotFoundException}
 * （会话内 id 理应存在，缺失通常意味账号已被注销/软删——由接口层翻译为 404，不吞错）。</p>
 */
@Service
public class GetSelfUserUseCase {

    private final UserRepository userRepository;

    /**
     * @param userRepository 用户仓储（domain 接口，infra 实现注入）
     */
    public GetSelfUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 按会话用户 id 查询本人账户聚合。
     *
     * <p>返回完整 {@link User} 聚合，由接口层投影为客户视图 {@code UserVO}（零敏感泄露：
     * 投影时<b>根本不读取</b> passwordHash/成本/上游模型/供应商等字段，从源头杜绝下发）。</p>
     *
     * @param userId 当前会话用户 id（self-scope，由接口层从认证主体注入）
     * @return 本人账户聚合
     * @throws UserNotFoundException 会话 id 对应用户不存在（已注销/软删），接口层翻译为 404
     */
    @Transactional(readOnly = true)
    public User getSelf(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
