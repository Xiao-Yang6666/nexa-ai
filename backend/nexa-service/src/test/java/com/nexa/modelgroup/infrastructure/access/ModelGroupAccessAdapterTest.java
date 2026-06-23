package com.nexa.modelgroup.infrastructure.access;

import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.model.ModelGroupAccess;
import com.nexa.modelgroup.domain.repository.ModelGroupAccessRepository;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;
import com.nexa.modelgroup.domain.vo.ModelGroupStatus;
import com.nexa.modelgroup.domain.vo.ModelNames;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelGroupAccessAdapter} 单测：私有组访问闸门放行/拒绝判定。
 */
@DisplayName("ModelGroupAccessAdapter 模型组访问闸门")
class ModelGroupAccessAdapterTest {

    private static final long USER = 10L;
    private static final long TOKEN = 20L;

    private StubGroupRepo groups;
    private StubAccessRepo access;
    private ModelGroupAccessAdapter adapter;

    @BeforeEach
    void setUp() {
        groups = new StubGroupRepo();
        access = new StubAccessRepo();
        adapter = new ModelGroupAccessAdapter(groups, access);
    }

    @Test
    @DisplayName("无对应模型组的分组 → 放行")
    void unknownGroupAllowed() {
        assertTrue(adapter.isAccessible("default", USER, TOKEN));
    }

    @Test
    @DisplayName("空白 code → 放行")
    void blankAllowed() {
        assertTrue(adapter.isAccessible("", USER, TOKEN));
        assertTrue(adapter.isAccessible(null, USER, TOKEN));
    }

    @Test
    @DisplayName("PUBLIC 组 → 放行")
    void publicAllowed() {
        groups.put(1L, "free", AccessPolicy.PUBLIC);
        assertTrue(adapter.isAccessible("free", USER, TOKEN));
    }

    @Test
    @DisplayName("AUTO_LEVEL 组 → 放行（闸门不收窄）")
    void autoLevelAllowed() {
        groups.put(2L, "auto", AccessPolicy.AUTO_LEVEL);
        assertTrue(adapter.isAccessible("auto", USER, TOKEN));
    }

    @Test
    @DisplayName("PRIVATE 组无授权 → 拒绝")
    void privateNoGrantDenied() {
        groups.put(3L, "premium", AccessPolicy.PRIVATE);
        assertFalse(adapter.isAccessible("premium", USER, TOKEN));
    }

    @Test
    @DisplayName("PRIVATE 组有 USER 级授权 → 放行")
    void privateUserGrantAllowed() {
        groups.put(3L, "premium", AccessPolicy.PRIVATE);
        access.save(ModelGroupAccess.grant(3L, AccessSubjectType.USER, USER, 1L));
        assertTrue(adapter.isAccessible("premium", USER, TOKEN));
    }

    @Test
    @DisplayName("PRIVATE 组有 TOKEN 级授权 → 放行")
    void privateTokenGrantAllowed() {
        groups.put(3L, "premium", AccessPolicy.PRIVATE);
        access.save(ModelGroupAccess.grant(3L, AccessSubjectType.TOKEN, TOKEN, 1L));
        assertTrue(adapter.isAccessible("premium", USER, TOKEN));
    }

    @Test
    @DisplayName("PRIVATE 组仅他人授权 → 拒绝（不串号）")
    void privateOtherSubjectDenied() {
        groups.put(3L, "premium", AccessPolicy.PRIVATE);
        access.save(ModelGroupAccess.grant(3L, AccessSubjectType.USER, 999L, 1L));
        assertFalse(adapter.isAccessible("premium", USER, TOKEN));
    }

    @Test
    @DisplayName("PRIVATE 组 tokenId 为 null → 仅按 user 维判定")
    void privateNullTokenUsesUser() {
        groups.put(3L, "premium", AccessPolicy.PRIVATE);
        access.save(ModelGroupAccess.grant(3L, AccessSubjectType.USER, USER, 1L));
        assertTrue(adapter.isAccessible("premium", USER, null));
    }

    // ===== 桩仓储 =====

    private static final class StubGroupRepo implements ModelGroupRepository {
        private final Map<String, ModelGroup> byCode = new HashMap<>();

        void put(long id, String code, AccessPolicy policy) {
            byCode.put(code, ModelGroup.builder()
                    .id(id).name(code).code(code).basePriceRatio(BigDecimal.ONE)
                    .models(ModelNames.of(List.of("gpt-4o"))).accessPolicy(policy)
                    .status(ModelGroupStatus.ENABLED).createdTime(1L).updatedTime(1L).build());
        }

        @Override public Optional<ModelGroup> findByCode(String code) {
            return Optional.ofNullable(byCode.get(code));
        }
        @Override public Optional<ModelGroup> findById(long id) { return Optional.empty(); }
        @Override public List<ModelGroup> findAll() { return List.of(); }
        @Override public List<ModelGroup> findByAccessPolicy(AccessPolicy p) { return List.of(); }
        @Override public List<ModelGroup> findByIds(List<Long> ids) { return List.of(); }
        @Override public boolean existsByCode(String code, Long excludeId) { return false; }
        @Override public ModelGroup save(ModelGroup g) { return g; }
        @Override public boolean softDelete(long id, long now) { return false; }
    }

    private static final class StubAccessRepo implements ModelGroupAccessRepository {
        private final Map<Long, ModelGroupAccess> store = new HashMap<>();
        private final AtomicLong seq = new AtomicLong(1);

        @Override public ModelGroupAccess save(ModelGroupAccess a) {
            long id = seq.getAndIncrement();
            a.assignId(id);
            store.put(id, a);
            return a;
        }
        @Override public boolean deleteById(long id) { return store.remove(id) != null; }
        @Override public List<ModelGroupAccess> findByModelGroupId(long gid) {
            return store.values().stream().filter(a -> a.modelGroupId() == gid).collect(Collectors.toList());
        }
        @Override public List<Long> findGroupIdsBySubject(AccessSubjectType t, long sid) {
            return new ArrayList<>();
        }
        @Override public boolean exists(long gid, AccessSubjectType t, long sid) {
            return store.values().stream().anyMatch(a -> a.modelGroupId() == gid
                    && a.subjectType() == t && a.subjectId() == sid);
        }
        @Override public Optional<ModelGroupAccess> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public boolean delete(long gid, AccessSubjectType t, long sid) { return false; }
    }
}
