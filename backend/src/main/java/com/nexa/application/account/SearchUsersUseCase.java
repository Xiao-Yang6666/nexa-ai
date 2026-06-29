package com.nexa.application.account;

import com.nexa.domain.account.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户分页搜索用例（应用服务，F-1008）。
 *
 * <p>编排「管理员分页浏览/搜索用户列表」（API-ENDPOINTS §1.4 {@code GET /api/user/} 列表、
 * {@code GET /api/user/search} 搜索）。本类<b>薄</b>：把命令转交领域仓储的
 * {@link UserRepository#search} 分页方法，再封装为应用层结果。搜索/分页/软删除过滤等
 * 持久化细节在基础设施层实现，应用层只依赖 domain 仓储接口（backend-engineer §2.3）。</p>
 *
 * <p>事务：只读查询，标 {@code readOnly} 提示底层走只读优化、不开写事务（无副作用）。</p>
 */
@Service
public class SearchUsersUseCase {

    private final UserRepository userRepository;

    /**
     * @param userRepository 用户仓储（domain 接口，infra 实现注入）
     */
    public SearchUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 执行用户分页搜索。
     *
     * @param command 搜索命令（keyword 可空=纯列表；page/pageSize 由仓储归一）
     * @return 当页用户聚合 + 分页元数据
     */
    @Transactional(readOnly = true)
    public SearchUsersResult search(SearchUsersCommand command) {
        // 仓储内部对 page/pageSize 做防御式下界归一（页码从 1 起、页大小 >0），keyword 空白=不过滤。
        UserRepository.Page<com.nexa.domain.account.model.User> page =
                userRepository.search(command.keyword(), command.page(), command.pageSize());
        return new SearchUsersResult(page.items(), page.total(), page.page(), page.pageSize());
    }
}
