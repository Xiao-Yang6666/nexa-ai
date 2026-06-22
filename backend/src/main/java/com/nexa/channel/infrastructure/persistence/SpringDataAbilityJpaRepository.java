package com.nexa.channel.infrastructure.persistence;

import com.nexa.channel.infrastructure.persistence.entity.AbilityJpaEntity;
import com.nexa.channel.infrastructure.persistence.entity.AbilityJpaEntity.AbilityPK;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Ability 路由索引 Spring Data 仓储（基础设施层，V25）。
 *
 * <p>CH-2 选渠主查询经 {@link #findSatisfied(String, String)} 拉取 (group, model, enabled=true)
 * 满足渠道的 priority/weight/channelId，由选渠适配器在内存中做优先级分层 + 加权随机抽签。</p>
 */
public interface SpringDataAbilityJpaRepository extends JpaRepository<AbilityJpaEntity, AbilityPK> {

    /**
     * 查询指定 (group, model) 下启用的 ability 行（CH-2 候选集）。
     *
     * @param group 分组
     * @param model 模型
     * @return 满足的 ability 行（含 priority/weight/channelId）
     */
    @Query("SELECT a FROM AbilityJpaEntity a WHERE a.group = :group AND a.model = :model AND a.enabled = true")
    List<AbilityJpaEntity> findSatisfied(@Param("group") String group, @Param("model") String model);

    /** 查询某渠道的全部 ability 行（fan-in 清理用）。 */
    List<AbilityJpaEntity> findByChannelId(Long channelId);

    /** 删除某渠道全部 ability 行（fan-in 清理）。 */
    @Modifying
    @Transactional
    @Query("DELETE FROM AbilityJpaEntity a WHERE a.channelId = :channelId")
    int deleteByChannelId(@Param("channelId") Long channelId);

    /** 按渠道批量置 enabled 状态（按 tag 批量启停 / 状态切换时维护）。 */
    @Modifying
    @Transactional
    @Query("UPDATE AbilityJpaEntity a SET a.enabled = :enabled WHERE a.channelId = :channelId")
    int updateEnabledByChannelId(@Param("channelId") Long channelId, @Param("enabled") boolean enabled);
}
