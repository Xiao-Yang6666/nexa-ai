package com.nexa.model.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 模型/供应商元数据入参非法异常（接口层映射 400）。
 *
 * <p>承载本片所有「缺失/非法入参」业务规则违反（领域规则来源 PRD ML-1 / BACKLOG T-115~T-120）：
 * <ul>
 *   <li>模型名为空 → 「模型名称不能为空」（F-3015）</li>
 *   <li>模型名重复 → 「模型名称已存在」（F-3015/F-3016 幂等键 model_name 冲突）</li>
 *   <li>更新缺少 id → 「缺少模型 ID」（F-3016）</li>
 *   <li>供应商名为空 → 「供应商名称不能为空」（F-3018）</li>
 *   <li>供应商名重复 → 「供应商名称已存在」（F-3018）</li>
 *   <li>更新缺少供应商 id → 「缺少供应商 ID」（F-3018）</li>
 *   <li>排行/价格 period/locale 等枚举非法（F-3022/F-3023）</li>
 * </ul>
 * </p>
 */
public class InvalidModelParameterException extends DomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "INVALID_MODEL_PARAMETER";

    /**
     * @param message 可读错误描述（如「模型名称已存在」）
     */
    public InvalidModelParameterException(String message) {
        super(CODE, message);
    }
}
