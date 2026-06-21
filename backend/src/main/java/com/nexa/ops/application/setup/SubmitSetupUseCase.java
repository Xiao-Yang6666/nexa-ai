package com.nexa.ops.application.setup;

import com.nexa.ops.application.port.RootUserProvisioner;
import com.nexa.ops.domain.exception.SystemAlreadyInitializedException;
import com.nexa.ops.domain.option.Option;
import com.nexa.ops.domain.option.OptionRepository;
import com.nexa.ops.domain.setup.SetupMarker;
import com.nexa.ops.domain.setup.SetupRepository;
import com.nexa.ops.domain.setup.SetupSubmission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统初始化提交用例（应用层，F-4016 POST /api/setup）。
 *
 * <p>事务边界用例：在单事务内完成「幂等占位 → 创建 root → 写模式开关 Option」三件副作用
 * （API-ENDPOINTS §9.1），任一失败整体回滚，不留半初始化状态。领域校验由 {@link SetupSubmission}
 * 构造期完成（充血），用例只编排（backend-engineer §2.1 application 薄）。</p>
 *
 * <p>幂等护栏：先以 {@link SetupRepository#saveIfAbsent} 原子占位，已存在即抛
 * {@link SystemAlreadyInitializedException}（→409）——把「占位」放在创建 root 之前，避免并发
 * 双提交各建一个 root 用户。</p>
 */
@Service
public class SubmitSetupUseCase {

    /** 引导版本标记（落 setups.version，预留升级识别）。 */
    private static final String BOOTSTRAP_VERSION = "v1";
    /** 自用模式开关 Option 键。 */
    private static final String KEY_SELF_USE_MODE = "SelfUseModeEnabled";
    /** 演示模式开关 Option 键。 */
    private static final String KEY_DEMO_SITE = "DemoSiteEnabled";

    private final SetupRepository setupRepository;
    private final RootUserProvisioner rootUserProvisioner;
    private final OptionRepository optionRepository;

    /**
     * @param setupRepository     初始化标记仓储
     * @param rootUserProvisioner root 用户开通端口
     * @param optionRepository    选项仓储（写模式开关）
     */
    public SubmitSetupUseCase(SetupRepository setupRepository,
                              RootUserProvisioner rootUserProvisioner,
                              OptionRepository optionRepository) {
        this.setupRepository = setupRepository;
        this.rootUserProvisioner = rootUserProvisioner;
        this.optionRepository = optionRepository;
    }

    /**
     * 提交系统初始化（创建 root + 模式开关，幂等）。
     *
     * @param submission 已通过领域校验的初始化提交
     * @throws SystemAlreadyInitializedException 系统已初始化（→409）
     */
    @Transactional
    public void execute(SetupSubmission submission) {
        // ① 原子占位（幂等护栏）：已存在标记即拒绝重复初始化。先占位再建 root，杜绝并发双建 root。
        boolean claimed = setupRepository.saveIfAbsent(SetupMarker.create(BOOTSTRAP_VERSION));
        if (!claimed) {
            throw new SystemAlreadyInitializedException();
        }

        // ② 创建 root 用户（Role=root, Quota=100000000）。密码哈希在端口实现内部完成。
        rootUserProvisioner.provisionRootUser(submission.username(), submission.rawPassword());

        // ③ 写模式开关 Option（覆盖式幂等）。
        optionRepository.save(Option.of(KEY_SELF_USE_MODE, Boolean.toString(submission.selfUseModeEnabled())));
        optionRepository.save(Option.of(KEY_DEMO_SITE, Boolean.toString(submission.demoSiteEnabled())));
    }
}
