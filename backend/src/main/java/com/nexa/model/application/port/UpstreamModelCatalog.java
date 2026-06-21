package com.nexa.model.application.port;

import com.nexa.model.domain.vo.UpstreamModel;

import java.util.List;

/**
 * 上游模型元数据拉取端口（应用层 / 防腐层接口，F-3019/F-3020）。
 *
 * <p>DDD 铁律：domain/application 只依赖本端口，不依赖具体上游 basellm HTTP 结构
 * （backend-engineer §2.3）。基础设施层实现本接口，封装上游 URL（按 locale 选址）、ETag/bodyCache、
 * JSON 解析，并产出领域值对象 {@link UpstreamModel}（防腐层把上游 DTO 翻成领域语言）。</p>
 *
 * <p>上游未配置/连接失败/解析失败时抛
 * {@link com.nexa.model.domain.exception.UpstreamSyncException}（接口层映射 502）。</p>
 */
public interface UpstreamModelCatalog {

    /**
     * 按 locale 拉取上游模型元数据全集（F-3019/F-3020）。
     *
     * @param locale 语言（en/zh-CN/zh-TW/ja，非法由实现回退默认 URL）
     * @return 上游模型条目列表（保序）
     */
    List<UpstreamModel> fetch(String locale);
}
