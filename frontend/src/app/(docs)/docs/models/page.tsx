import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, Endpoint, H2, H3,
  InlineLink, Lede, PageTitle, ParamTable, Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '模型列表 API · Nexa·AI 文档',
  description: '列出当前账号可用的所有模型。',
};

const TOC: TocItem[] = [
  { id: 'endpoint', label: '端点', level: 2 },
  { id: 'request', label: '请求示例', level: 2 },
  { id: 'response', label: '响应', level: 2 },
  { id: 'response-fields', label: '响应字段', level: 3 },
];

const REQ: CodePane[] = [
  { lang: 'curl', label: 'curl', code: `curl https://api.nexa.ai/v1/models \\
  -H "Authorization: Bearer $NEXA_KEY"` },
  { lang: 'python', label: 'Python', code: `from openai import OpenAI

client = OpenAI(
    base_url="https://api.nexa.ai/v1",
    api_key="sk-nexa-...",
)
models = client.models.list()
for m in models:
    print(m.id)` },
  { lang: 'node', label: 'Node', code: `import OpenAI from "openai";

const client = new OpenAI({
  baseURL: "https://api.nexa.ai/v1",
  apiKey: process.env.NEXA_API_KEY,
});
const models = await client.models.list();
for (const m of models.data) {
  console.log(m.id);
}` },
  { lang: 'go', label: 'Go', code: `package main

import (
    "context"
    "fmt"
    "github.com/sashabaranov/go-openai"
)

func main() {
    cfg := openai.DefaultConfig("sk-nexa-...")
    cfg.BaseURL = "https://api.nexa.ai/v1"
    client := openai.NewClientWithConfig(cfg)

    models, _ := client.ListModels(context.Background())
    for _, m := range models.Models {
        fmt.Println(m.ID)
    }
}` },
];

const RESP: CodePane[] = [{ lang: 'json', label: '响应 JSON', code: `{
  "object": "list",
  "data": [
    {
      "id": "gpt-4o",
      "object": "model",
      "created": 1715367049,
      "owned_by": "openai"
    },
    {
      "id": "claude-3-5-sonnet-20241022",
      "object": "model",
      "created": 1729296000,
      "owned_by": "anthropic"
    },
    {
      "id": "deepseek-chat",
      "object": "model",
      "created": 1704067200,
      "owned_by": "deepseek"
    }
  ]
}` }];

/** /docs/models：模型列表 API 页。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="API 参考" page="模型列表 API" />
      <PageTitle>模型列表 API</PageTitle>
      <Lede>列出当前账号可用的所有模型。返回的 ID 可直接用于聊天补全、嵌入、图像等接口的 <code>model</code> 字段。</Lede>

      <Section>
        <H2 id="endpoint">端点</H2>
        <Endpoint method="GET" path="https://api.nexa.ai/v1/models" />
        <p>无请求体，需在 <code>Authorization</code> 头携带有效的 Bearer Token，详见 <InlineLink href="/docs/authentication">认证说明</InlineLink>。</p>
      </Section>

      <Section>
        <H2 id="request">请求示例</H2>
        <CodeBlock panes={REQ} />
      </Section>

      <Section>
        <H2 id="response">响应</H2>
        <p>返回一个 <code>list</code> 对象，<code>data</code> 数组包含所有可用模型的摘要信息。</p>
        <H3 id="response-fields">响应字段</H3>
        <ParamTable rows={[
          { name: 'object', type: 'string', desc: <>固定为 <code>list</code>。</> },
          { name: 'data', type: 'array', desc: '模型对象数组，每项含以下字段。' },
          { name: 'data[].id', type: 'string', desc: '模型唯一标识，可直接传入请求的 model 字段。' },
          { name: 'data[].object', type: 'string', desc: <>固定为 <code>model</code>。</> },
          { name: 'data[].created', type: 'integer', desc: '模型发布时间的 Unix 时间戳。' },
          { name: 'data[].owned_by', type: 'string', desc: '模型提供方，如 openai / anthropic / deepseek。' },
        ]} />
        <CodeBlock panes={RESP} />
        <Callout tone="info">
          <strong>按能力筛选。</strong>如需仅列出支持嵌入或图像生成的模型，可在控制台的<InlineLink href="/dashboard">模型列表</InlineLink>页查看各模型的能力标签。
        </Callout>
      </Section>
    </DocsShell>
  );
}
