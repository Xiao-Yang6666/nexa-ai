package com.nexa.infrastructure.model.sync;

import com.nexa.application.model.port.ChannelModelCatalog;
import com.nexa.application.model.port.UpstreamModelCatalog;
import com.nexa.domain.model.vo.UpstreamModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 上游模型目录实现：以「渠道引用的模型名」为同步来源（基础设施层 adapter，F-3019/F-3020）。
 *
 * <p>设计取舍：真实 basellm 官方元数据 URL 拉取尚未接入；但平台的实际诉求是「渠道声明支持了某模型 A，
 * 模型库（ModelMeta）就该有对应记录」——这正是缺失检测（{@link ChannelModelCatalog#referencedModelNames()}）
 * 的同一数据源。故本 adapter 把「渠道引用的模型名」作为待同步上游目录产出 {@link UpstreamModel}，
 * 让「缺失模型检测」与「上游模型同步」口径一致：检测出的缺失模型，在同步预览里可见、可一键补齐登记。</p>
 *
 * <p>供应商名按模型名常见前缀启发式推断（gpt/o1→OpenAI、claude→Anthropic、gemini→Google 等），
 * 推不出则留空（同步仍创建模型，仅无 vendor 关联）。description/icon/tags/endpoints 留空，
 * 由后续真实 basellm 接入或人工补全。</p>
 */
@Component
public class ChannelReferencedUpstreamModelCatalog implements UpstreamModelCatalog {

    private final ChannelModelCatalog channelCatalog;

    /** @param channelCatalog 渠道模型目录端口（跨上下文只读，提供渠道引用的模型名集） */
    public ChannelReferencedUpstreamModelCatalog(ChannelModelCatalog channelCatalog) {
        this.channelCatalog = channelCatalog;
    }

    /** {@inheritDoc} */
    @Override
    public List<UpstreamModel> fetch(String locale) {
        List<String> referenced = channelCatalog.referencedModelNames();
        List<UpstreamModel> result = new ArrayList<>(referenced.size());
        for (String name : referenced) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String vendor = inferVendor(name);
            result.add(new UpstreamModel(name.trim(), vendor, null, null, null, null));
        }
        return result;
    }

    /**
     * 按模型名常见前缀启发式推断供应商名（推不出返回 null → 同步不关联 vendor）。
     *
     * @param modelName 模型名
     * @return 供应商名或 null
     */
    private String inferVendor(String modelName) {
        String n = modelName.toLowerCase(Locale.ROOT);
        if (n.startsWith("gpt") || n.startsWith("o1") || n.startsWith("o3") || n.startsWith("o4")
                || n.startsWith("text-") || n.startsWith("dall-e") || n.startsWith("whisper")
                || n.startsWith("chatgpt") || n.startsWith("codex")) {
            return "OpenAI";
        }
        if (n.startsWith("claude")) {
            return "Anthropic";
        }
        if (n.startsWith("gemini") || n.startsWith("palm") || n.startsWith("text-bison")) {
            return "Google";
        }
        if (n.startsWith("deepseek")) {
            return "DeepSeek";
        }
        if (n.startsWith("qwen") || n.startsWith("qwq")) {
            return "Alibaba";
        }
        if (n.startsWith("glm") || n.startsWith("chatglm")) {
            return "Zhipu";
        }
        if (n.startsWith("moonshot") || n.startsWith("kimi")) {
            return "Moonshot";
        }
        if (n.startsWith("grok")) {
            return "xAI";
        }
        if (n.startsWith("mistral") || n.startsWith("mixtral")) {
            return "Mistral";
        }
        return null;
    }
}
