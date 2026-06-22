package com.nexa.prefill.application;

import com.nexa.prefill.domain.exception.InvalidPrefillParameterException;
import com.nexa.prefill.domain.exception.PrefillGroupNameConflictException;
import com.nexa.prefill.domain.exception.PrefillGroupNotFoundException;
import com.nexa.prefill.domain.model.PrefillGroup;
import com.nexa.prefill.domain.repository.PrefillGroupRepository;
import com.nexa.prefill.domain.vo.PrefillItems;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 更新预填分组用例（F-2013，PRD 模块十五 §14「名称冲突校验」）。
 *
 * <p>应用层编排：按 id 取存活分组（不存在 → 404）→ 若改名则做<b>名称冲突校验</b>（新 name 与同
 * type <b>他组</b>重名 → 409，改回自身原名不算冲突）→ 经聚合行为方法 {@link PrefillGroup#rename}/
 * {@link PrefillGroup#replaceItems} 变更（充血）→ 持久化。type 不可更新（openapi UpdateRequest
 * 无 type）。事务边界在此，保证「查重 + 更新」原子。</p>
 */
@Service
public class UpdatePrefillGroupUseCase {

    private final PrefillGroupRepository repository;

    /**
     * @param repository 预填分组仓储
     */
    public UpdatePrefillGroupUseCase(PrefillGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 更新预填分组（部分更新：name/items 各自为 null 时不变）。
     *
     * @param command 更新命令（id 必填；name/items 可空）
     * @return 更新后的分组聚合
     * @throws InvalidPrefillParameterException  id 缺失或 name 非法（→400）
     * @throws PrefillGroupNotFoundException     id 不存在/已删除（→404）
     * @throws PrefillGroupNameConflictException 新 name 与同 type 他组冲突（→409）
     */
    @Transactional
    public PrefillGroup update(UpdatePrefillGroupCommand command) {
        if (command.id() == null || command.id() <= 0) {
            throw new InvalidPrefillParameterException("prefill group id is required");
        }
        long id = command.id();
        PrefillGroup group = repository.findById(id)
                .orElseThrow(() -> new PrefillGroupNotFoundException(id));

        long now = Instant.now().getEpochSecond();

        // 改名分支：先在聚合内校验新 name 格式（rename 含格式校验），再查同 type 他组冲突。
        if (command.name() != null) {
            // 经聚合行为方法改名（充血，格式非法在此抛 → 400）。
            group.rename(command.name(), now);
            // 名称冲突校验：排除自身 id（改回原名/未实际改名命中自身不算冲突，PRD §14）。
            if (repository.existsByTypeAndName(group.type(), group.name(), id)) {
                throw new PrefillGroupNameConflictException(group.name());
            }
        }
        // 改条目分支：整体替换（null = 不改，replaceItems 内有 null 守卫）。
        if (command.items() != null) {
            group.replaceItems(PrefillItems.of(command.items()), now);
        }
        return repository.save(group);
    }
}
