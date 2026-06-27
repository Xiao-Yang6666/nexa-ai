package com.nexa.channel.infrastructure.persistence;

import com.nexa.channel.domain.exception.ChannelUpstreamException;
import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.channel.domain.vo.MultiKeyMode;
import com.nexa.channel.domain.vo.Pagination;
import com.nexa.channel.infrastructure.persistence.entity.ChannelJpaEntity;
import com.nexa.shared.persistence.JsonbCodec;
import com.nexa.shared.persistence.PageQueries;
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
    private final JsonbCodec jsonbCodec;

    /**
     * @param jpa          Spring Data JPA 仓库（infra 内部依赖）
     * @param jsonbCodec   JSONB 编解码器（channel_info 值对象 ↔ jsonb 串，统一 wrap 异常）
     */
    public ChannelRepositoryImpl(SpringDataChannelJpaRepository jpa,
                                 JsonbCodec jsonbCodec) {
        this.jpa = jpa;
        this.jsonbCodec = jsonbCodec;
    }

    /** {@inheritDoc} */
    @Override
    public Channel save(Channel channel) {
        ChannelJpaEntity entity = toEntity(channel);
        ChannelJpaEntity saved = jpa.save(entity);
        channel.assignId(saved.getId());
        // 路由索引（abilities）已下沉至账号域（account_id 维度），channel 不再维护 ability fan-out。
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
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
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
        Pageable pageable = PageQueries.of(pagination.page(), pagination.pageSize());
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
        return Channel.rehydrate(
                e.getId(),
                e.getType(),
                e.getKey(),
                e.getStatus(),
                e.getName(),
                e.getWeight(),
                e.getBaseUrl(),
                e.getModels(),
                e.getGroup(),
                e.getPriority(),
                e.getAutoBan(),
                e.getBalance(),
                e.getUsedQuota(),
                e.getResponseTime(),
                e.getTestTime(),
                e.getModelMapping(),
                e.getStatusCodeMapping(),
                e.getTag(),
                e.getSetting(),
                deserializeChannelInfo(e.getChannelInfo()),
                e.getCreatedTime());
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
        ChannelInfoJson dto = new ChannelInfoJson(
                info.multiKey(), info.multiKeySize(), info.mode().wire(), info.pollingIndex());
        return jsonbCodec.write(dto);
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
        ChannelInfoJson dto = jsonbCodec.read(json, ChannelInfoJson.class);
        return new ChannelInfo(dto.isMultiKey(), dto.multiKeySize(),
                MultiKeyMode.fromWire(dto.multiKeyMode()), dto.multiKeyPollingIndex());
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

    // ---- 领域聚合 <-> JPA 实体映射结束 ----
}
