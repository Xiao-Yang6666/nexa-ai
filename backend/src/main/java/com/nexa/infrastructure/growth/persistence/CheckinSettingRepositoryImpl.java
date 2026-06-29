package com.nexa.infrastructure.growth.persistence;

import com.nexa.domain.growth.exception.GrowthPersistenceException;
import com.nexa.domain.growth.repository.CheckinSettingRepository;
import com.nexa.domain.growth.vo.CheckinSetting;
import com.nexa.infrastructure.growth.persistence.entity.CheckinSettingJpaEntity;
import org.springframework.stereotype.Repository;

/**
 * 领域仓储 {@link CheckinSettingRepository} 的 JPA 实现（基础设施层适配器，PRD GR-3）。
 *
 * <p>签到配置落库为单行表（固定主键 {@link CheckinSettingJpaEntity#SINGLETON_ID}=1）。读取无记录时
 * 回落 {@link CheckinSetting#defaults()}（DB-SCHEMA §12 默认 enabled=false/min=1000/max=10000）——
 * 系统初次部署、管理员未保存过配置时签到接口仍以「关闭」缺省行为运行。保存即 upsert 单行。</p>
 */
@Repository
public class CheckinSettingRepositoryImpl implements CheckinSettingRepository {

    private final SpringDataCheckinSettingJpaRepository jpa;

    /** @param jpa Spring Data JPA 仓库（infra 内部依赖） */
    public CheckinSettingRepositoryImpl(SpringDataCheckinSettingJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** {@inheritDoc} */
    @Override
    public CheckinSetting load() {
        return jpa.findById(CheckinSettingJpaEntity.SINGLETON_ID)
                .map(e -> CheckinSetting.of(
                        Boolean.TRUE.equals(e.getEnabled()),
                        e.getMinQuota() == null ? CheckinSetting.DEFAULT_MIN_QUOTA : e.getMinQuota(),
                        e.getMaxQuota() == null ? CheckinSetting.DEFAULT_MAX_QUOTA : e.getMaxQuota()))
                .orElseGet(CheckinSetting::defaults);
    }

    /** {@inheritDoc} */
    @Override
    public void save(CheckinSetting setting) {
        try {
            CheckinSettingJpaEntity e = new CheckinSettingJpaEntity();
            e.setId(CheckinSettingJpaEntity.SINGLETON_ID); // 固定单例主键，save 即 upsert 覆盖
            e.setEnabled(setting.enabled());
            e.setMinQuota(setting.minQuota());
            e.setMaxQuota(setting.maxQuota());
            jpa.save(e);
        } catch (RuntimeException ex) {
            throw new GrowthPersistenceException("save checkin setting failed", ex);
        }
    }
}
