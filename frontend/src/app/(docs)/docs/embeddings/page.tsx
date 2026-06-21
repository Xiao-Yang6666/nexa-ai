import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, Endpoint, H2,
  InlineLink, Lede, PageTitle, ParamTable, Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '嵌入 API · Nexa·AI 文档',
  description: '把文本转换为向量表示，用于语义检索、聚类、推荐等场景。完全兼容 OpenAI Embeddings 协议。',
};

const TOC: TocItem[] = [
  { id: 'endpoint', label: '端点', level: 2 },
  { id: 'request', label: '请求示例', level: 2 },
  { id: 'params', label: '请求参数', level: 2 },
  { id: 'response', label: '响应', level: 2 },
];

const REQ: CodePane[] = [
  { lang: 'curl', label: 'curl', code: `curl https://api.nexa.ai/v1/embeddings \\
  -H "Authorization: Bearer $NEXA_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "text-embedding-3-small",
    "input": "向量数据库是什么？"
  }'` },
  { lang: 'python', label: 'Python', code: `from openai import OpenAI

client = OpenAI(
    base_url="https://api.nexa.ai/v1",
    api_key="sk-nexa-...",
)
resp = client.embeddings.create(
    model="text-embedding-3-small",
    input="向量数据库是什么？",
)
print(resp.data[0].embedding[:8])` },
  { lang: 'node', label: 'Node', code: `import OpenAI from "openai";

const client = new OpenAI({
  baseURL: "https://api.nexa.ai/v1",
  apiKey: process.env.NEXA_API_KEY,
});
const resp = await client.embeddings.create({
  model: "text-embedding-3-small",
  input: "向量数据库是什么？",
});
console.log(resp.data[0].embedding.slice(0, 8));` },
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

    resp, _ := client.CreateEmbeddings(context.Background(), openai.EmbeddingRequest{
        Model: openai.SmallEmbedding3,
        Input: []string{"向量数据库是什么？"},
    })
    fmt.Println(resp.Data[0].Embedding[:8])
}` },
];

const RESP: CodePane[] = [{ lang: 'json', label: '响应 JSON', code: `{
  "object": "list",
  "data": [{
    "object": "embedding",
    "index": 0,
    "embedding": [0.0023064255, -0.009327292, 0.015797347, "..."]
  }],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}` }];

/** /docs/embeddings：嵌入 API 页。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="API 参考" page="嵌入 API" />
      <PageTitle>嵌入 API</PageTitle>
      <Lede>把文本转换为浮点向量表示，用于语义检索、相似度计算、聚类与推荐等场景。完全兼容 OpenAI Embeddings 协议。</Lede>

      <Section>
        <H2 id="endpoint">端点</H2>
        <Endpoint method="POST" path="https://api.nexa.ai/v1/embeddings" />
        <p>需在 <code>Authorization</code> 头携带有效的 Bearer Token，详见 <InlineLink href="/docs/authentication">认证说明</InlineLink>。<code>model</code> 与 <code>input</code> 为必填字段。</p>
      </Section>

      <Section>
        <H2 id="request">请求示例</H2>
        <CodeBlock panes={REQ} />
      </Section>

      <Section>
        <H2 id="params">请求参数</H2>
        <ParamTable rows={[
          { name: 'model', type: 'string', required: true, desc: <>嵌入模型 ID，如 <code>text-embedding-3-small</code>、<code>text-embedding-3-large</code>。</> },
          { name: 'input', type: 'string / array', required: true, desc: '待嵌入的文本，可为单条字符串或字符串数组（批量）。' },
          { name: 'encoding_format', type: 'string', desc: <>向量编码格式：<code>float</code>（默认）或 <code>base64</code>。</> },
          { name: 'dimensions', type: 'integer', desc: '输出向量维度（仅部分模型支持降维）。' },
          { name: 'user', type: 'string', desc: '代表终端用户的稳定标识，用于滥用监测。' },
        ]} />
        <Callout tone="info">
          <strong>批量更省。</strong>把多条文本放进 <code>input</code> 数组一次请求，比逐条调用更省时省额度，返回的 <code>data</code> 数组按 <code>index</code> 对应输入顺序。
        </Callout>
      </Section>

      <Section>
        <H2 id="response">响应</H2>
        <p>返回一个 <code>list</code> 对象，<code>data</code> 数组中每项含一条向量。</p>
        <ParamTable rows={[
          { name: 'object', type: 'string', desc: <>固定为 <code>list</code>。</> },
          { name: 'data', type: 'array', desc: '嵌入对象数组，按输入顺序排列。' },
          { name: 'data[].embedding', type: 'array', desc: '浮点数向量（维度取决于模型）。' },
          { name: 'data[].index', type: 'integer', desc: '对应 input 数组中的位置。' },
          { name: 'model', type: 'string', desc: '实际服务本次请求的模型 ID。' },
          { name: 'usage', type: 'object', desc: <>token 用量：<code>prompt_tokens</code>、<code>total_tokens</code>。</> },
        ]} />
        <CodeBlock panes={RESP} />
      </Section>
    </DocsShell>
  );
}
