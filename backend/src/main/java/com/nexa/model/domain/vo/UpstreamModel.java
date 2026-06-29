package com.nexa.model.domain.vo;

/**
 * 上游模型条目值对象（同步领域服务输入，F-3019/F-3020）。
 *
 * <p>表达从上游 basellm 元数据拉取到的单个模型描述（防腐层产出，领域不依赖上游 JSON 结构）。
 * 上游用 vendorName（字符串）标识供应商，本地需 upsert 成 vendor_id；同步领域服务在比对/落地时
 * 完成该解析（backend-engineer §2.4 防腐层产出领域值对象，不让上游 DTO 渗入领域）。</p>
 *
 * @param modelName   上游模型名（幂等键）
 * @param vendorName  上游供应商名（可空/空白 → 无供应商）
 * @param description 上游描述（可空）
 * @param icon        上游图标（可空）
 * @param tags        上游标签串（可空）
 * @param endpoints   上游支持端点串（可空）
 */
public record UpstreamModel(String modelName,
                            String vendorName,
                            String description,
                            String icon,
                            String tags,
                            String endpoints) {

    /** 规范构造：modelName 去空白校验（上游脏数据防御）。 */
    public UpstreamModel {
        modelName = modelName == null ? "" : modelName.trim();
    }

    /** @return modelName 是否有效（非空白） */
    public boolean isValid() {
        return !modelName.isEmpty();
    }

    /** @return 供应商名是否有效（非空白） */
    public boolean hasVendor() {
        return vendorName != null && !vendorName.isBlank();
    }
}
