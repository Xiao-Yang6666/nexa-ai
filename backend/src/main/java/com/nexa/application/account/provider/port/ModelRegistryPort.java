package com.nexa.application.account.provider.port;

import java.util.List;

/**
 * 模型登记端口（账号域出站端口，跨 bounded context 写集成）。
 *
 * <p>账号域「获取模型列表」探测到上游模型后，经本端口把这些模型名登记进平台「模型管理」
 * （model 上下文的 ModelMeta 元数据表），实现「探测即自动入库」（用户拍板方案 1）。</p>
 *
 * <p>DDD：account 上下文只声明所需写能力（防腐层），适配器在 infrastructure 层用
 * {@code com.nexa.model} 的领域仓储实现，不让 model 领域对象渗出端口（context 间防腐，
 * backend-engineer §2.5）。这是 account→model 的「上下文间集成」写入点，与 model→channel
 * 的只读 {@code ChannelModelCatalog} 互为镜像。</p>
 */
public interface ModelRegistryPort {

    /**
     * 幂等登记模型名集合到模型管理：已存在（按 model_name 幂等键）的跳过，仅新建缺失的。
     *
     * <p>调用方（探测用例）保证传入的是去空白去空的模型名。本端口的实现必须幂等——重复探测
     * 同一批模型不应抛重名异常，也不应产生重复记录，只统计本次真正新建的数量。</p>
     *
     * @param modelNames 待登记的模型名列表（可空/空 → 不做任何事，返回 0）
     * @return 本次实际新建的模型数量（已存在的不计入）
     */
    int registerModelsIfAbsent(List<String> modelNames);
}
