package com.nexa.infrastructure.growth.persistence;

import com.nexa.domain.growth.exception.GrowthPersistenceException;
import com.nexa.domain.growth.repository.CheckinSettingRepository;
import com.nexa.domain.growth.vo.CheckinSetting;
import com.nexa.infrastructure.growth.persistence.po.CheckinSettingPO;
import org.springframework.stereotype.Repository;

/**
 * 领域仓储 {@link CheckinSettingRepository} 的 MyBatis-Plus 实现（基础设施层适配器，PRD GR-3）。
 *
 * <p>签到配置落库为单行表（固定主键 {@link CheckinSettingPO#SINGLETON_ID}=1）。读取无记录时
 * 回落 {@link CheckinSetting#defaults()}（DB-SCHEMA §12 默认 enabled=false/min=1000/max=10000）——
 * 系统初次部署、管理员未保存过配置时签到接口仍以「关闭」缺省行为运行。保存即 upsert 单行：固定主键
 * 由领域指定（{@code IdType.INPUT}），故按「先 selectById 判断存在与否，再 insert / updateById」
 * 复现原 JPA merge 语义。</p>
 */
@Repository
public class CheckinSettingRepositoryImpl implements CheckinSettingRepository {

    private final CheckinSettingMapper mapper;

    /** @param mapper MyBatis-Plus Mapper（infra 内部依赖） */
    public CheckinSettingRepositoryImpl(CheckinSettingMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public CheckinSetting load() {
        CheckinSettingPO e = mapper.selectById(CheckinSettingPO.SINGLETON_ID);
        if (e == null) {
            return CheckinSetting.defaults();
        }
        return CheckinSetting.of(
                Boolean.TRUE.equals(e.getEnabled()),
                e.getMinQuota() == null ? CheckinSetting.DEFAULT_MIN_QUOTA : e.getMinQuota(),
                e.getMaxQuota() == null ? CheckinSetting.DEFAULT_MAX_QUOTA : e.getMaxQuota());
    }

    /** {@inheritDoc} */
    @Override
    public void save(CheckinSetting setting) {
        try {
            CheckinSettingPO e = new CheckinSettingPO();
            e.setId(CheckinSettingPO.SINGLETON_ID); // 固定单例主键，upsert 覆盖
            e.setEnabled(setting.enabled());
            e.setMinQuota(setting.minQuota());
            e.setMaxQuota(setting.maxQuota());
            // 固定主键非自增：存在则 updateById，否则 insert（复现原 JPA save 的 merge 语义）。
            if (mapper.selectById(CheckinSettingPO.SINGLETON_ID) == null) {
                mapper.insert(e);
            } else {
                mapper.updateById(e);
            }
        } catch (RuntimeException ex) {
            throw new GrowthPersistenceException("save checkin setting failed", ex);
        }
    }
}
