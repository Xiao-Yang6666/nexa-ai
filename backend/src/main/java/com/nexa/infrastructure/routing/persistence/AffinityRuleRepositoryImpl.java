package com.nexa.infrastructure.routing.persistence;

import com.nexa.infrastructure.routing.persistence.mapper.AffinitySettingsMapper;

import com.nexa.infrastructure.routing.persistence.mapper.AffinityRuleMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.domain.routing.exception.AffinityPersistenceException;
import com.nexa.domain.routing.model.AffinityRule;
import com.nexa.domain.routing.repository.AffinityRuleRepository;
import com.nexa.domain.routing.vo.AffinitySettings;
import com.nexa.domain.routing.vo.KeySource;
import com.nexa.domain.routing.vo.KeySourceType;
import com.nexa.infrastructure.routing.persistence.po.AffinityRulePO;
import com.nexa.infrastructure.routing.persistence.po.AffinitySettingsPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 领域仓储 {@link AffinityRuleRepository} 的 MyBatis-Plus 实现（基础设施层适配器，F-2031）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link AffinityRuleMapper} + {@link AffinitySettingsMapper}
 * + 实体↔领域映射实现它。规则 save / 策略 saveSettings 均为「先查后写」的 merge（存在更新、不存在插入）。
 * JSONB 字段 key_sources/pass_headers 由 Jackson 互转（与 ChannelRepositoryImpl 同构模式）。本表无软删，
 * 按名删走物理 {@code delete}。</p>
 */
@Repository
public class AffinityRuleRepositoryImpl implements AffinityRuleRepository {

    private final AffinityRuleMapper ruleMapper;
    private final AffinitySettingsMapper settingsMapper;
    private final ObjectMapper objectMapper;

    /** 亲和策略单行哨兵主键（CHECK(id=1) 强制单例）。 */
    private static final int SETTINGS_ID = 1;

    /**
     * @param ruleMapper     MyBatis-Plus 规则 Mapper（infra 内部依赖）
     * @param settingsMapper MyBatis-Plus 策略 Mapper（infra 内部依赖）
     * @param objectMapper   JSON 编解码器（key_sources/pass_headers 值对象 ↔ jsonb 串）
     */
    public AffinityRuleRepositoryImpl(AffinityRuleMapper ruleMapper,
                                      AffinitySettingsMapper settingsMapper,
                                      ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.settingsMapper = settingsMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(AffinityRule rule) {
        // merge 语义：按名命中则更新现有行（保留主键），否则插入新行。
        AffinityRulePO existing = selectByName(rule.name());
        AffinityRulePO entity = existing != null ? existing : new AffinityRulePO();
        mapToEntity(rule, entity);
        if (existing != null) {
            ruleMapper.updateById(entity);
        } else {
            ruleMapper.insert(entity);
        }
    }

    @Override
    public Optional<AffinityRule> findByName(String name) {
        return Optional.ofNullable(selectByName(name)).map(this::toDomain);
    }

    @Override
    public List<AffinityRule> findEnabledRules() {
        // enabled = true ORDER BY built_in DESC, name ASC
        return ruleMapper.selectList(Wrappers.<AffinityRulePO>lambdaQuery()
                        .eq(AffinityRulePO::isEnabled, true)
                        .orderByDesc(AffinityRulePO::isBuiltIn)
                        .orderByAsc(AffinityRulePO::getName))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<AffinityRule> findAll() {
        // ORDER BY built_in DESC, name ASC
        return ruleMapper.selectList(Wrappers.<AffinityRulePO>lambdaQuery()
                        .orderByDesc(AffinityRulePO::isBuiltIn)
                        .orderByAsc(AffinityRulePO::getName))
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(String name) {
        // 物理按名删（本表无软删），原 @Modifying DELETE 1:1 对应。
        ruleMapper.delete(Wrappers.<AffinityRulePO>lambdaQuery()
                .eq(AffinityRulePO::getName, name));
    }

    @Override
    public AffinitySettings loadSettings() {
        AffinitySettingsPO e = settingsMapper.selectById(SETTINGS_ID);
        return e == null
                ? AffinitySettings.defaults()
                : new AffinitySettings(e.isEnabled(), e.isSwitchOnSuccess(), e.getMaxEntries(), e.getDefaultTtlSeconds());
    }

    @Override
    @Transactional
    public void saveSettings(AffinitySettings settings) {
        // merge 语义：单例 id=1 已存在则更新，否则插入。
        AffinitySettingsPO existing = settingsMapper.selectById(SETTINGS_ID);
        AffinitySettingsPO entity = existing != null ? existing : new AffinitySettingsPO();
        entity.setId(SETTINGS_ID);
        entity.setEnabled(settings.enabled());
        entity.setSwitchOnSuccess(settings.switchOnSuccess());
        entity.setMaxEntries(settings.maxEntries());
        entity.setDefaultTtlSeconds(settings.defaultTtlSeconds());
        entity.setUpdatedTime(Instant.now().getEpochSecond());
        if (existing != null) {
            settingsMapper.updateById(entity);
        } else {
            settingsMapper.insert(entity);
        }
    }

    // ---- 映射方法 ----

    /**
     * 按规则名查单条。
     *
     * @param name 规则名
     * @return 命中返回 PO，否则 null
     */
    private AffinityRulePO selectByName(String name) {
        return ruleMapper.selectOne(Wrappers.<AffinityRulePO>lambdaQuery()
                .eq(AffinityRulePO::getName, name));
    }

    private void mapToEntity(AffinityRule rule, AffinityRulePO e) {
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

    private AffinityRule toDomain(AffinityRulePO e) {
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
