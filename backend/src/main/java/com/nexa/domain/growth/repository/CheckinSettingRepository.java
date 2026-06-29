package com.nexa.domain.growth.repository;

import com.nexa.domain.growth.vo.CheckinSetting;

/**
 * 签到配置仓储接口（领域层定义，基础设施层实现，PRD GR-3 / FC-022）。
 *
 * <p>DB-SCHEMA §12 注：CheckinSetting 走 KV（{@code operation_setting/checkin_setting.go}）。本仓储
 * 抽象「读当前配置 / 保存配置」两项能力，domain/application 只依赖接口，不关心其落库为单行表还是
 * KV（backend-engineer §2.3）。读取无记录时实现回落 {@link CheckinSetting#defaults()}（DB-SCHEMA §12
 * 默认 enabled=false/min=1000/max=10000）。</p>
 */
public interface CheckinSettingRepository {

    /**
     * 读取当前签到配置（GR-1/GR-2/GR-3 入口都先读）。
     *
     * @return 当前配置；无持久化记录时返回系统缺省（{@link CheckinSetting#defaults()}）
     */
    CheckinSetting load();

    /**
     * 保存签到配置（GR-3 管理端 S5「保存配置」，保存后立即影响签到接口行为）。
     *
     * <p>入参已是通过区间校验的合法 {@link CheckinSetting}（值对象构造期已守护 min&lt;=max、非负），
     * 仓储只负责落库。</p>
     *
     * @param setting 合法签到配置
     */
    void save(CheckinSetting setting);
}
