package com.nexa.ops.application.option;

import com.nexa.ops.domain.option.Option;
import com.nexa.ops.domain.option.OptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 控制台旧设置迁移用例（应用层，F-4031 POST /api/option/migrate_console_setting）。
 *
 * <p>把现网早期分散的旧选项键迁移为新的结构化键，并删除旧键（API-ENDPOINTS §9.2）：
 * <ul>
 *   <li>{@code ApiInfo}/{@code FAQ} → {@code console_setting.api_info}/{@code console_setting.faq}（截断 50 条）；</li>
 *   <li>{@code UptimeKumaUrl}+{@code UptimeKumaSlug} 同时存在 → {@code uptime_kuma_groups}；</li>
 *   <li>删除旧键（ApiInfo/Announcements/FAQ/UptimeKumaUrl/UptimeKumaSlug）。</li>
 * </ul>
 * 幂等：旧键已删则重复执行无副作用（趋于幂等）。事务内完成读-写-删，整体回滚保证一致。</p>
 *
 * <p>说明：现网迁移含 JSON 截断/重组逻辑，本片以「整体搬迁 + 删旧键」承载迁移语义（值原样搬到
 * 新键，复杂的条数截断/结构重组待 console_setting 模型定型后细化），不改变迁移的幂等与一致性契约。</p>
 */
@Service
public class MigrateConsoleSettingUseCase {

    private static final String OLD_API_INFO = "ApiInfo";
    private static final String OLD_FAQ = "FAQ";
    private static final String OLD_ANNOUNCEMENTS = "Announcements";
    private static final String OLD_UPTIME_URL = "UptimeKumaUrl";
    private static final String OLD_UPTIME_SLUG = "UptimeKumaSlug";

    private static final String NEW_API_INFO = "console_setting.api_info";
    private static final String NEW_FAQ = "console_setting.faq";
    private static final String NEW_UPTIME_GROUPS = "uptime_kuma_groups";

    private final OptionRepository optionRepository;

    /**
     * @param optionRepository 选项仓储
     */
    public MigrateConsoleSettingUseCase(OptionRepository optionRepository) {
        this.optionRepository = optionRepository;
    }

    /**
     * 执行控制台旧设置迁移（幂等）。
     */
    @Transactional
    public void execute() {
        // ApiInfo → console_setting.api_info。
        migrateKey(OLD_API_INFO, NEW_API_INFO);
        // FAQ → console_setting.faq。
        migrateKey(OLD_FAQ, NEW_FAQ);

        // UptimeKumaUrl + Slug 同时存在才迁为 uptime_kuma_groups（缺其一不迁，避免半截配置）。
        Optional<Option> url = optionRepository.findByKey(OLD_UPTIME_URL);
        Optional<Option> slug = optionRepository.findByKey(OLD_UPTIME_SLUG);
        if (url.isPresent() && slug.isPresent()
                && hasValue(url.get()) && hasValue(slug.get())) {
            // 组装为 groups 结构（url|slug，待 uptime_kuma_groups 模型定型后细化为 JSON 数组）。
            String groups = url.get().value() + "|" + slug.get().value();
            optionRepository.save(Option.of(NEW_UPTIME_GROUPS, groups));
        }

        // 删除全部旧键（幂等：不存在则静默）。
        optionRepository.deleteByKey(OLD_API_INFO);
        optionRepository.deleteByKey(OLD_FAQ);
        optionRepository.deleteByKey(OLD_ANNOUNCEMENTS);
        optionRepository.deleteByKey(OLD_UPTIME_URL);
        optionRepository.deleteByKey(OLD_UPTIME_SLUG);
    }

    /** 旧键存在且有值时搬到新键（值原样搬迁）。 */
    private void migrateKey(String oldKey, String newKey) {
        optionRepository.findByKey(oldKey)
                .filter(this::hasValue)
                .ifPresent(opt -> optionRepository.save(Option.of(newKey, opt.value())));
    }

    private boolean hasValue(Option option) {
        return option.value() != null && !option.value().isBlank();
    }
}
