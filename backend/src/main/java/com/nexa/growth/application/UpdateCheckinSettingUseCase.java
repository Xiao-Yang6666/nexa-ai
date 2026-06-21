package com.nexa.growth.application;

import com.nexa.growth.domain.repository.CheckinSettingRepository;
import com.nexa.growth.domain.vo.CheckinSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 签到配置管理用例（PRD GR-3 签到开关与额度区间配置，管理端，F-1049）。
 *
 * <p>应用层用例：薄编排。区间合法性（{@code Min<=Max}、非负）由值对象 {@link CheckinSetting} 构造期
 * 守护（充血校验，backend-engineer §2.4）——本用例只把入参构造成合法值对象（非法即在构造时抛
 * {@code InvalidCheckinSettingException}）再落库，保存后立即影响签到接口行为（GR-3 S5）。</p>
 */
@Service
public class UpdateCheckinSettingUseCase {

    private final CheckinSettingRepository settingRepository;

    /** @param settingRepository 签到配置仓储 */
    public UpdateCheckinSettingUseCase(CheckinSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    /**
     * 读取当前签到配置（管理端回显，GR-3 S1）。
     *
     * @return 当前配置（无记录返回系统缺省）
     */
    @Transactional(readOnly = true)
    public CheckinSetting current() {
        return settingRepository.load();
    }

    /**
     * 保存签到配置（GR-3 S2~S5）。
     *
     * <p>把入参装配成 {@link CheckinSetting} 值对象——区间非法（Min&gt;Max / 负）在此构造时即抛
     * {@code InvalidCheckinSettingException}（GR-3 S3/S4 拒绝保存，配置不变）；合法则落库。</p>
     *
     * @param enabled  是否启用
     * @param minQuota 最小奖励额度
     * @param maxQuota 最大奖励额度
     * @return 已保存的合法配置（回显）
     * @throws com.nexa.growth.domain.exception.InvalidCheckinSettingException 区间非法
     */
    @Transactional
    public CheckinSetting update(boolean enabled, long minQuota, long maxQuota) {
        CheckinSetting setting = CheckinSetting.of(enabled, minQuota, maxQuota); // 构造期校验区间
        settingRepository.save(setting);
        return setting;
    }
}
