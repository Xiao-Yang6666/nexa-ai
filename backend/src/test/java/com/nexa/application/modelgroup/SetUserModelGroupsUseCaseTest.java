package com.nexa.application.modelgroup;

import com.nexa.domain.modelgroup.exception.ModelGroupCodeNotFoundException;
import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.model.ModelGroupAccess;
import com.nexa.domain.modelgroup.repository.ModelGroupAccessRepository;
import com.nexa.domain.modelgroup.repository.ModelGroupRepository;
import com.nexa.domain.modelgroup.vo.AccessPolicy;
import com.nexa.domain.modelgroup.vo.AccessSubjectType;
import com.nexa.domain.modelgroup.vo.ModelGroupStatus;
import com.nexa.domain.modelgroup.vo.ModelNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SetUserModelGroupsUseCase} 覆盖式设置用户私有组授权用例单测（纯内存桩仓储，不起 Spring/DB）。
 *
 * <p>覆盖 diff 增删语义：新增、删多余、保持不动、清空、code 无效 → 404。</p>
 */
@DisplayName("SetUserModelGroupsUseCase 覆盖式设置用户私有组")
class SetUserModelGroupsUseCaseTest {

    private static final long USER_ID = 100L;

    private InMemoryModelGroupRepo groupRepo;
    private InMemoryAccessRepo accessRepo;
    private SetUserModelGroupsUseCase useCase;

    @BeforeEach
    void setUp() {
        groupRepo = new InMemoryModelGroupRepo();
        accessRepo = new InMemoryAccessRepo();
        useCase = new SetUserModelGroupsUseCase(groupRepo, accessRepo);
        // 预置三个存活模型组：basic(1)/premium(2)/enterprise(3)。
        groupRepo.put(1L, "basic");
        groupRepo.put(2L, "premium");
        groupRepo.put(3L, "enterprise");
    }

    @Test
    @DisplayName("空 → 设两个：新增两条 USER 授权")
    void grantFromEmpty() {
        useCase.setForUser(USER_ID, List.of("basic", "premium"));
        List<Long> ids = accessRepo.findGroupIdsBySubject(AccessSubjectType.USER, USER_ID);
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("已有 basic+premium → 设 premium+enterprise：删 basic、加 enterprise、留 premium")
    void diffAddRemoveKeep() {
        // 初始：basic + premium。
        accessRepo.save(ModelGroupAccess.grant(1L, AccessSubjectType.USER, USER_ID, 1L));
        accessRepo.save(ModelGroupAccess.grant(2L, AccessSubjectType.USER, USER_ID, 1L));

        useCase.setForUser(USER_ID, List.of("premium", "enterprise"));

        List<Long> ids = accessRepo.findGroupIdsBySubject(AccessSubjectType.USER, USER_ID);
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(2L, 3L))); // premium 保留、enterprise 新增、basic 删除
    }

    @Test
    @DisplayName("空目标集 → 清空全部 USER 授权")
    void clearAll() {
        accessRepo.save(ModelGroupAccess.grant(1L, AccessSubjectType.USER, USER_ID, 1L));
        useCase.setForUser(USER_ID, List.of());
        assertTrue(accessRepo.findGroupIdsBySubject(AccessSubjectType.USER, USER_ID).isEmpty());
    }

    @Test
    @DisplayName("null 目标集 → 等价清空")
    void nullClears() {
        accessRepo.save(ModelGroupAccess.grant(1L, AccessSubjectType.USER, USER_ID, 1L));
        useCase.setForUser(USER_ID, null);
        assertTrue(accessRepo.findGroupIdsBySubject(AccessSubjectType.USER, USER_ID).isEmpty());
    }

    @Test
    @DisplayName("不影响其它用户的授权")
    void otherUserUntouched() {
        accessRepo.save(ModelGroupAccess.grant(1L, AccessSubjectType.USER, 999L, 1L));
        useCase.setForUser(USER_ID, List.of("premium"));
        assertEquals(List.of(1L), accessRepo.findGroupIdsBySubject(AccessSubjectType.USER, 999L));
    }

    @Test
    @DisplayName("含无效 code → 抛 404，且不产生任何变更（事务语义由调用方保证，此处验证抛出）")
    void invalidCodeRejected() {
        assertThrows(ModelGroupCodeNotFoundException.class,
                () -> useCase.setForUser(USER_ID, List.of("basic", "not_exist")));
    }

    // ===== 内存桩仓储 =====

    private static final class InMemoryModelGroupRepo implements ModelGroupRepository {
        private final Map<Long, ModelGroup> byId = new HashMap<>();
        private final Map<String, Long> codeToId = new HashMap<>();

        void put(long id, String code) {
            ModelGroup g = ModelGroup.builder()
                    .id(id).name(code).code(code).basePriceRatio(BigDecimal.ONE)
                    .models(ModelNames.of(List.of("gpt-4o"))).accessPolicy(AccessPolicy.PRIVATE)
                    .status(ModelGroupStatus.ENABLED).createdTime(1L).updatedTime(1L).build();
            byId.put(id, g);
            codeToId.put(code, id);
        }

        @Override
        public Optional<ModelGroup> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ModelGroup> findByCode(String code) {
            Long id = codeToId.get(code);
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<ModelGroup> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public List<ModelGroup> findByAccessPolicy(AccessPolicy accessPolicy) {
            return byId.values().stream()
                    .filter(g -> g.accessPolicy() == accessPolicy).collect(Collectors.toList());
        }

        @Override
        public List<ModelGroup> findByIds(List<Long> ids) {
            return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        @Override
        public boolean existsByCode(String code, Long excludeId) {
            Long id = codeToId.get(code);
            return id != null && !id.equals(excludeId);
        }

        @Override
        public ModelGroup save(ModelGroup group) {
            return group;
        }

        @Override
        public boolean softDelete(long id, long nowEpochSec) {
            return byId.remove(id) != null;
        }
    }

    private static final class InMemoryAccessRepo implements ModelGroupAccessRepository {
        private final Map<Long, ModelGroupAccess> store = new HashMap<>();
        private final AtomicLong seq = new AtomicLong(1);

        @Override
        public ModelGroupAccess save(ModelGroupAccess access) {
            long id = seq.getAndIncrement();
            access.assignId(id);
            store.put(id, access);
            return access;
        }

        @Override
        public boolean deleteById(long id) {
            return store.remove(id) != null;
        }

        @Override
        public List<ModelGroupAccess> findByModelGroupId(long modelGroupId) {
            return store.values().stream()
                    .filter(a -> a.modelGroupId() == modelGroupId).collect(Collectors.toList());
        }

        @Override
        public List<Long> findGroupIdsBySubject(AccessSubjectType subjectType, long subjectId) {
            return store.values().stream()
                    .filter(a -> a.subjectType() == subjectType && a.subjectId() == subjectId)
                    .map(ModelGroupAccess::modelGroupId).collect(Collectors.toList());
        }

        @Override
        public boolean exists(long modelGroupId, AccessSubjectType subjectType, long subjectId) {
            return store.values().stream().anyMatch(a -> a.modelGroupId() == modelGroupId
                    && a.subjectType() == subjectType && a.subjectId() == subjectId);
        }

        @Override
        public Optional<ModelGroupAccess> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean delete(long modelGroupId, AccessSubjectType subjectType, long subjectId) {
            Long key = store.entrySet().stream()
                    .filter(e -> e.getValue().modelGroupId() == modelGroupId
                            && e.getValue().subjectType() == subjectType
                            && e.getValue().subjectId() == subjectId)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (key == null) {
                return false;
            }
            store.remove(key);
            return true;
        }
    }
}
