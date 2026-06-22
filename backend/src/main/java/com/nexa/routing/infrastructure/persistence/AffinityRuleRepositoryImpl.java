package com.nexa.routing.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.routing.domain.exception.AffinityPersistenceException;
import com.nexa.routing.domain.model.AffinityRule;
import com.nexa.routing.domain.repository.AffinityRuleRepository;
import com.nexa.routing.domain.vo.AffinitySettings;
import com.nexa.routing.domain.vo.KeySource;
import com.nexa.routing.domain.vo.KeySourceType;
import com.nexa.routing.infrastructure.persistence.entity.AffinityRuleJpaEntity;
import com.nexa.routing.infrastructure.persistence.entity.AffinitySettingsJpaEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 领域仓储 {@link AffinityRuleRepository} 的 JPA 实现（基础设施层适配器，F-2031）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataAffinityRuleJpaRepository} +
 * {@link SpringDataAffinitySettingsJpaRepository} + 实体↔领域映射实现它。JSONB 字段 key_sources/pass_headers
 * 由 Jackson 互转（与 ChannelRepositoryImpl 同构模式）。</p>
 */
@Repository
public class AffinityRuleRepositoryImpl implements AffinityRuleRepository {

    private final SpringDataAffinityRuleJpaRepository jpaRule;
    private final SpringDataAffinitySettingsJpaRepository jpaSettings;
    private final ObjectMapper objectMapper;

    /**
     * @param jpaRule      Spring Data JPA 规则仓库
     * @param jpaSettings  Spring Data JPA 策略仓库
     * @param objectMapper JSON 编解码器（key_sources/pass_headers 值对象 ↔ jsonb 串）
     */
    public AffinityRuleRepositoryImpl(SpringDataAffinityRuleJpaRepository jpaRule,
                                      SpringDataAffinitySettingsJpaRepository jpaSettings,
                                      ObjectMapper objectMapper) {
        this.jpaRule = jpaRule;
        this.jpaSettings = jpaSettings;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(AffinityRule rule) {
        Optional<AffinityRuleJpaEntity> existing = jpaRule.findByName(rule.name());
        AffinityRuleJpaEntity entity = existing.orElseGet(AffinityRuleJpaEntity::new);
        mapToEntity(rule, entity);
        jpaRule.save(entity);
    }

    @Override
    public Optional<AffinityRule> findByName(String name) {
        return jpaRule.findByName(name).map(this::toDomain);
    }

    @Override
    public List<AffinityRule> findEnabledRules() {
        return jpaRule.findAllEnabled().stream().map(this::toDomain).toList();
    }

    @Override
    public List<AffinityRule> findAll() {
        return jpaRule.findAllSorted().stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(String name) {
        jpaRule.deleteByName(name);
    }

    @Override
    public AffinitySettings loadSettings() {
        return jpaSettings.findById(1)
                .map(e -> new AffinitySettings(e.isEnabled(), e.isSwitchOnSuccess(), e.getMaxEntries(), e.getDefaultTtlSeconds()))
                .orElse(AffinitySettings.defaults());
    }

    @Override
    public void saveSettings(AffinitySettings settings) {
        AffinitySettingsJpaEntity entity = jpaSettings.findById(1).orElseGet(AffinitySettingsJpaEntity::new);
        entity.setId(1);
        entity.setEnabled(settings.enabled());
        entity.setSwitchOnSuccess(settings.switchOnSuccess());
        entity.setMaxEntries(settings.maxEntries());
        entity.setDefaultTtlSeconds(settings.defaultTtlSeconds());
        entity.setUpdatedTime(Instant.now().getEpochSecond());
        jpaSettings.save(entity);
    }

    // ---- 映射方法 ----

    private void mapToEntity(AffinityRule rule, AffinityRuleJpaEntity e) {
        e.setName(rule.name());
        e.setEnabled(rule.enabled());
        e.setModelRegex(rule.modelRegex());
        e.setPathRegex(rule.pathRegex());
        e.setKeySources(serializeKeySources(rule.keySources()));
        e.setPassHeaders(serializePassHeaders(rule.passHeaders()));
        e.setSkipRetryOnFailure(rule.skipRetryOnFailure());
        e.setTtlSeconds(rule.ttlSeconds());
        e.setBuiltIn(rule.builtIn());
        long now = Instant.now().getEpochSecond();
        if (e.getCreatedTime() == null) {
            e.setCreatedTime(now);
        }
        e.setUpdatedTime(now);
    }

    private AffinityRule toDomain(AffinityRuleJpaEntity e) {
        return AffinityRule.builder()
                .enabled(e.isEnabled())
                .name(e.getName())
                .modelRegex(e.getModelRegex())
                .pathRegex(e.getPathRegex())
                .keySources(deserializeKeySources(e.getKeySources()))
                .passHeaders(deserializePassHeaders(e.getPassHeaders()))
                .skipRetryOnFailure(e.isSkipRetryOnFailure())
                .ttlSeconds(e.getTtlSeconds())
                .builtIn(e.isBuiltIn())
                .build();
    }

    private String serializeKeySources(List<KeySource> keySources) {
        if (keySources == null || keySources.isEmpty()) {
            return "[]";
        }
        try {
            List<KeySourceJson> list = keySources.stream()
                    .map(ks -> new KeySourceJson(ks.type().wire(), ks.path()))
                    .toList();
            return objectMapper.writeValueAsString(list);
        } catch (Exception ex) {
            throw new AffinityPersistenceException("failed to serialize key_sources", ex);
        }
    }

    private List<KeySource> deserializeKeySources(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return List.of();
        }
        try {
            List<KeySourceJson> list = objectMapper.readValue(json, new TypeReference<List<KeySourceJson>>() {
            });
            return list.stream()
                    .map(ks -> new KeySource(KeySourceType.fromWire(ks.type()), ks.path()))
                    .toList();
        } catch (Exception ex) {
            throw new AffinityPersistenceException("failed to deserialize key_sources", ex);
        }
    }

    private String serializePassHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception ex) {
            throw new AffinityPersistenceException("failed to serialize pass_headers", ex);
        }
    }

    private Map<String, String> deserializePassHeaders(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            throw new AffinityPersistenceException("failed to deserialize pass_headers", ex);
        }
    }

    private record KeySourceJson(String type, String path) {
    }
}
