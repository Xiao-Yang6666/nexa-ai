package com.nexa.channel.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexa.channel.domain.exception.ChannelUpstreamException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.channel.domain.vo.MultiKeyMode;
import com.nexa.channel.domain.vo.Pagination;
import com.nexa.channel.infrastructure.persistence.entity.AbilityJpaEntity;
import com.nexa.channel.infrastructure.persistence.entity.ChannelJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 领域仓储 {@link ChannelRepository} 的 JPA 实现（基础设施层适配器，F-2016~F-2028）。
 *
 * <p>DDD 依赖倒置落地：domain 定义接口，本类用 {@link SpringDataChannelJpaRepository}
 * + 实体↔领域映射实现它。领域聚合 {@link Channel} 与 JPA 实体 {@link ChannelJpaEntity} 分离，
 * 映射集中在此处，domain 因此不感知 Hibernate（backend-engineer §2.3）。</p>
 *
 * <p>{@code channel_info} JSONB 列以 String 落库，本层用 Jackson 与领域值对象 {@link ChannelInfo}
 * 互转——领域层因此持有强类型值对象而非裸 JSON 串。序列化/反序列化失败 wrap 为
 * {@link ChannelUpstreamException}（不吞错；持久化层数据损坏属可观测异常）。</p>
 */
@Repository
public class ChannelRepositoryImpl implements ChannelRepository {

    private final SpringDataChannelJpaRepository jpa;
    private final SpringDataAbilityJpaRepository abilityJpa;
    private final ObjectMapper objectMapper;

    /**
     * @param jpa          Spring Data JPA 仓库（infra 内部依赖）
     * @param abilityJpa   Ability 路由索引仓储（save/delete 时维护 fan-out）
     * @param objectMapper JSON 编解码器（channel_info 值对象 ↔ jsonb 串）
     */
    public ChannelRepositoryImpl(SpringDataChannelJpaRepository jpa,
                                 SpringDataAbilityJpaRepository abilityJpa,
                                 ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.abilityJpa = abilityJpa;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Channel save(Channel channel) {
        ChannelJpaEntity entity = toEntity(channel);
        ChannelJpaEntity saved = jpa.save(entity);
        channel.assignId(saved.getId());
        // fan-out：以 (group, models) 重建该 channel 的 abilities 行。
        rebuildAbilities(saved.getId(), saved.getGroup(), saved.getModels(),
                saved.getPriority(), saved.getWeight(), saved.getTag(),
                saved.getStatus() == 1);
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Channel> findById(long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public List<Channel> findPage(String group, Integer type, String tag, Integer status, Pagination pagination) {
        Pageable pageable = PageRequest.of(pagination.page() - 1, pagination.pageSize());
        return jpa.findPage(group, type, tag, status, pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long count(String group, Integer type, String tag, Integer status) {
        return jpa.countFiltered(group, type, tag, status);
    }

    /** {@inheritDoc} */
    @Override
    public List<Channel> search(String keyword, Pagination pagination) {
        Pageable pageable = PageRequest.of(pagination.page() - 1, pagination.pageSize());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            // 空关键词等价无过滤全量分页（复用过滤查询，四维全 null）。
            return jpa.findPage(null, null, null, null, pageable).stream().map(this::toDomain).toList();
        }
        return jpa.searchByKeyword(kw, pageable).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public long countSearch(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            return jpa.countFiltered(null, null, null, null);
        }
        return jpa.countByKeyword(kw);
    }

    /** {@inheritDoc} */
    @Override
    public List<Channel> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Channel> findByTag(String tag) {
        return jpa.findByTag(tag).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Channel> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public void deleteById(long id) {
        // fan-in：清掉该 channel 的 abilities（不依赖 DB CASCADE 外键）。
        abilityJpa.deleteByChannelId(id);
        jpa.deleteById(id);
    }

    /** {@inheritDoc} */
    @Override
    public int updateStatusByIds(List<Long> ids, ChannelStatus status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 逐条加载-改状态-保存：渠道批量启停量级小（管理操作），走聚合方法保证状态迁移走领域护栏，
        // 不绕过聚合直接 UPDATE（DDD：状态变更经聚合，backend-engineer §2.2）。
        List<ChannelJpaEntity> entities = jpa.findByIdIn(ids);
        for (ChannelJpaEntity e : entities) {
            e.setStatus(status.code());
        }
        jpa.saveAll(entities);
        // 同步 abilities.enabled（渠道禁用→候选集剔除，启用→恢复）。
        boolean enabled = status.code() == 1;
        for (ChannelJpaEntity e : entities) {
            abilityJpa.updateEnabledByChannelId(e.getId(), enabled);
        }
        return entities.size();
    }

    // ---- 领域聚合 <-> JPA 实体映射（基础设施层内部，领域不可见） ----

    /**
     * 领域聚合 → JPA 实体（持久化方向）。
     *
     * @param c 领域聚合
     * @return 待持久化的 JPA 实体
     */
    private ChannelJpaEntity toEntity(Channel c) {
        ChannelJpaEntity e = new ChannelJpaEntity();
        e.setId(c.id());
        e.setType(c.type().code());
        e.setKey(c.key());
        e.setStatus(c.status().code());
        e.setName(c.name());
        e.setWeight(c.weight());
        e.setBaseUrl(c.baseUrl());
        e.setModels(c.models());
        e.setGroup(c.group());
        e.setPriority(c.priority());
        e.setAutoBan(c.autoBan());
        e.setBalance(c.balance());
        e.setUsedQuota(c.usedQuota());
        e.setResponseTime(c.responseTime());
        e.setTestTime(c.testTime());
        e.setModelMapping(c.modelMapping());
        e.setStatusCodeMapping(c.statusCodeMapping());
        e.setTag(c.tag());
        e.setSetting(c.setting());
        e.setChannelInfo(serializeChannelInfo(c.channelInfo()));
        e.setCreatedTime(c.createdTime());
        return e;
    }

    /**
     * JPA 实体 → 领域聚合（重建方向，走 {@link Channel#builder()} 具名链式装配）。
     *
     * <p>null/空白归一（baseUrl/models/group/balance/statusCodeMapping/channelInfo 兜底、
     * type/status 枚举解析）已收敛进 {@link Channel.Builder}，本处只做字段透传，不再散落三元兜底。</p>
     *
     * @param e JPA 实体
     * @return 重建的领域聚合
     */
    private Channel toDomain(ChannelJpaEntity e) {
        return Channel.builder()
                .id(e.getId())
                .type(e.getType())
                .key(e.getKey())
                .status(e.getStatus())
                .name(e.getName())
                .weight(e.getWeight())
                .baseUrl(e.getBaseUrl())
                .models(e.getModels())
                .group(e.getGroup())
                .priority(e.getPriority())
                .autoBan(e.getAutoBan())
                .balance(e.getBalance())
                .usedQuota(e.getUsedQuota())
                .responseTime(e.getResponseTime())
                .testTime(e.getTestTime())
                .modelMapping(e.getModelMapping())
                .statusCodeMapping(e.getStatusCodeMapping())
                .tag(e.getTag())
                .setting(e.getSetting())
                .channelInfo(deserializeChannelInfo(e.getChannelInfo()))
                .createdTime(e.getCreatedTime())
                .build();
    }

    /**
     * 领域 {@link ChannelInfo} → channel_info JSONB 串。
     *
     * @param info 多 Key 信息值对象（可空）
     * @return JSON 串（null/单 Key 缺省 → null，省空间）
     */
    private String serializeChannelInfo(ChannelInfo info) {
        if (info == null || (!info.multiKey() && info.multiKeySize() == 0 && info.pollingIndex() == 0)) {
            return null;
        }
        try {
            ChannelInfoJson dto = new ChannelInfoJson(
                    info.multiKey(), info.multiKeySize(), info.mode().wire(), info.pollingIndex());
            return objectMapper.writeValueAsString(dto);
        } catch (Exception ex) {
            // 序列化失败属编程/数据异常，不吞错（保留错误链）。
            throw new ChannelUpstreamException("failed to serialize channel_info", ex);
        }
    }

    /**
     * channel_info JSONB 串 → 领域 {@link ChannelInfo}。
     *
     * @param json JSON 串（可空）
     * @return 多 Key 信息值对象（null/空 → 单 Key 缺省）
     */
    private ChannelInfo deserializeChannelInfo(String json) {
        if (json == null || json.isBlank()) {
            return ChannelInfo.single();
        }
        try {
            ChannelInfoJson dto = objectMapper.readValue(json, ChannelInfoJson.class);
            return new ChannelInfo(dto.isMultiKey(), dto.multiKeySize(),
                    MultiKeyMode.fromWire(dto.multiKeyMode()), dto.multiKeyPollingIndex());
        } catch (Exception ex) {
            throw new ChannelUpstreamException("failed to deserialize channel_info", ex);
        }
    }

    /**
     * channel_info JSONB 的扁平 DTO（仅基础设施层内部用于 Jackson 编解码，字段名对齐 openapi ChannelInfo）。
     *
     * @param isMultiKey            是否多 Key
     * @param multiKeySize          Key 数量
     * @param multiKeyMode          轮询模式串
     * @param multiKeyPollingIndex  轮询游标
     */
    private record ChannelInfoJson(boolean isMultiKey, int multiKeySize,
                                   String multiKeyMode, int multiKeyPollingIndex) {
    }

    // ---- Ability 路由索引 fan-out / fan-in（V25，CH-2 选渠子系统） ----

    /**
     * 重建某个渠道的 ability 行（channel save 后调用）。
     *
     * <p>策略：先按 channelId 全删，再按 {@code models} 逗号分隔串 fan-out 插入
     * {@code (group, model, channel_id, priority, weight, tag, enabled)} 元组。
     * 用 TRIM 处理空白条目；空/空白 model 跳过；{@code models} 为空时仅清空不插入。</p>
     *
     * @param channelId 渠道 id
     * @param group     渠道分组
     * @param models    支持模型（逗号分隔）
     * @param priority  优先级
     * @param weight    权重
     * @param tag       标签
     * @param enabled   是否启用（channel.status==1）
     */
    private void rebuildAbilities(Long channelId, String group, String models,
                                  long priority, int weight, String tag, boolean enabled) {
        if (channelId == null) return;
        abilityJpa.deleteByChannelId(channelId);
        if (group == null || models == null || models.isBlank()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : Arrays.asList(models.split(","))) {
            String m = raw == null ? "" : raw.trim();
            if (!m.isEmpty()) seen.add(m);
        }
        if (seen.isEmpty()) return;
        List<AbilityJpaEntity> rows = new ArrayList<>(seen.size());
        for (String m : seen) {
            rows.add(new AbilityJpaEntity(group, m, channelId, enabled, priority, weight, tag));
        }
        abilityJpa.saveAll(rows);
    }
}
