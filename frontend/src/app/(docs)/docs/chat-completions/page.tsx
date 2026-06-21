import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, Endpoint, H2,
  InlineLink, Lede, PageTitle, ParamTable, Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '聊天补全 API · Nexa·AI 文档',
  description: '给定一组对话消息，模型返回一条补全回复。完全兼容 OpenAI Chat Completions 协议。',
};

const TOC: TocItem[] = [
  { id: 'endpoint', label: '端点', level: 2 },
  { id: 'request', label: '请求示例', level: 2 },
  { id: 'params', label: '请求参数', level: 2 },
  { id: 'response', label: '响应', level: 2 },
  { id: 'stream', label: '流式响应', level: 2 },
];

const REQ: CodePane[] = [
  { lang: 'curl', label: 'curl', code: `curl https://api.nexa.ai/v1/chat/completions \\
  -H "Authorization: Bearer $NEXA_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "system", "content": "你是一个简洁的助手。"},
      {"role": "user", "content": "什么是向量数据库？"}
    ],
    "temperature": 0.7,
    "max_tokens": 512
  }'` },
  { lang: 'python', label: 'Python', code: `from openai import OpenAI
client = OpenAI(
    base_url="https://api.nexa.ai/v1",
    api_key="sk-nexa-...",
)
resp = client.chat.completions.create(
    model="gpt-4o",
    messages=[
        {"role": "system", "content": "你是一个简洁的助手。"},
        {"role": "user", "content": "什么是向量数据库？"},
    ],
    temperature=0.7,
    max_tokens=512,
)
print(resp.choices[0].message.content)` },
  { lang: 'node', label: 'Node', code: `import OpenAI from "openai";
const client = new OpenAI({
  baseURL: "https://api.nexa.ai/v1",
  apiKey: process.env.NEXA_API_KEY,
});
const resp = await client.chat.completions.create({
  model: "gpt-4o",
  messages: [
    { role: "system", content: "你是一个简洁的助手。" },
    { role: "user", content: "什么是向量数据库？" },
  ],
  temperature: 0.7,
  max_tokens: 512,
});
console.log(resp.choices[0].message.content);` },
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
    resp, _ := client.CreateChatCompletion(context.Background(), openai.ChatCompletionRequest{
        Model: "gpt-4o",
        Messages: []openai.ChatCompletionMessage{
            {Role: "system", Content: "你是一个简洁的助手。"},
            {Role: "user", Content: "什么是向量数据库？"},
        },
        Temperature: 0.7,
        MaxTokens: 512,
    })
    fmt.Println(resp.Choices[0].Message.Content)
}` },
];

const RESP: CodePane[] = [{ lang: 'json', label: '响应 JSON', code: `{
  "id": "chatcmpl-b3e1f9a2",
  "object": "chat.completion",
  "created": 1718857800,
  "model": "gpt-4o",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "向量数据库是一种为高维向量的相似度检索而优化的数据库。"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 29,
    "completion_tokens": 34,
    "total_tokens": 63
  }
}` }];

const STREAM: CodePane[] = [
  { lang: 'curl', label: 'curl', code: `curl https://api.nexa.ai/v1/chat/completions \\
  -H "Authorization: Bearer $NEXA_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{"model":"gpt-4o","messages":[{"role":"user","content":"数到三"}],"stream":true}'` },
  { lang: 'python', label: 'Python', code: `stream = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "数到三"}],
    stream=True,
)
for chunk in stream:
    delta = chunk.choices[0].delta.content or ""
    print(delta, end="", flush=True)` },
  { lang: 'node', label: 'Node', code: `const stream = await client.chat.completions.create({
  model: "gpt-4o",
  messages: [{ role: "user", content: "数到三" }],
  stream: true,
});
for await (const chunk of stream) {
  process.stdout.write(chunk.choices[0].delta.content ?? "");
}` },
  { lang: 'go', label: 'Go', code: `stream, _ := client.CreateChatCompletionStream(context.Background(), openai.ChatCompletionRequest{
    Model: "gpt-4o",
    Messages: []openai.ChatCompletionMessage{{Role: "user", Content: "数到三"}},
    Stream: true,
})
defer stream.Close()
for {
    resp, err := stream.Recv()
    if err != nil { break }
    fmt.Print(resp.Choices[0].Delta.Content)
}` },
];

const SSE: CodePane[] = [{ lang: 'text', label: 'SSE 数据流', code: `data: {"id":"chatcmpl-c4...","object":"chat.completion.chunk","choices":[{"delta":{"role":"assistant"},"index":0}]}
data: {"choices":[{"delta":{"content":"一"},"index":0}]}
data: {"choices":[{"delta":{"content":"、二、三"},"index":0}]}
data: {"choices":[{"delta":{},"index":0,"finish_reason":"stop"}]}
data: [DONE]` }];

export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="API 参考" page="聊天补全 API" />
      <PageTitle>聊天补全 API</PageTitle>
      <Lede>给定一组对话消息，模型返回一条补全回复。这是 Nexa·AI 最核心的接口，完全兼容 OpenAI Chat Completions 协议，支持多轮对话、函数调用、流式输出与多模态输入。</Lede>

      <Section>
        <H2 id="endpoint">端点</H2>
        <Endpoint method="POST" path="https://api.nexa.ai/v1/chat/completions" />
        <p>需要在 <code>Authorization</code> 头携带有效的 Bearer Token，详见 <InlineLink href="/docs/authentication">认证说明</InlineLink>。请求体为 JSON，<code>model</code> 与 <code>messages</code> 为必填字段。</p>
      </Section>

      <Section>
        <H2 id="request">请求示例</H2>
        <CodeBlock panes={REQ} />
      </Section>

      <Section>
        <H2 id="params">请求参数</H2>
        <p>请求体为 JSON 对象，支持以下字段：</p>
        <ParamTable rows={[
          { name: 'model', type: 'string', required: true, desc: <>要使用的模型 ID，如 <code>gpt-4o</code>、<code>claude-3-5-sonnet</code>。完整列表见 <InlineLink href="/docs/models">模型列表</InlineLink>。</> },
          { name: 'messages', type: 'array', required: true, desc: <>对话消息数组，每个元素含 <code>role</code>（system / user / assistant / tool）与 <code>content</code>。</> },
          { name: 'temperature', type: 'number', desc: '采样温度，范围 0–2，默认 1。值越高输出越随机。' },
          { name: 'top_p', type: 'number', desc: '核采样阈值，范围 0–1，默认 1。' },
          { name: 'max_tokens', type: 'integer', desc: '本次补全生成的最大 token 数，不含输入。' },
          { name: 'stream', type: 'boolean', desc: <>为 <code>true</code> 时以 SSE 流式逐块返回，默认 <code>false</code>。</> },
          { name: 'stop', type: 'string / array', desc: '最多 4 个停止序列，模型生成到任一序列即停止。' },
          { name: 'presence_penalty', type: 'number', desc: '范围 -2–2，正值鼓励谈及新话题，默认 0。' },
          { name: 'frequency_penalty', type: 'number', desc: '范围 -2–2，正值降低重复用词的概率，默认 0。' },
          { name: 'n', type: 'integer', desc: '为每条输入生成的补全条数，默认 1。' },
          { name: 'tools', type: 'array', desc: '可供模型调用的工具（函数）定义列表。' },
          { name: 'tool_choice', type: 'string / object', desc: <>控制是否及如何调用工具：<code>auto</code> / <code>none</code> / 指定函数。</> },
          { name: 'response_format', type: 'object', desc: <>设为 <code>{"{\"type\":\"json_object\"}"}</code> 强制返回合法 JSON。</> },
          { name: 'user', type: 'string', desc: '代表终端用户的稳定标识，用于滥用监测。' },
        ]} />
        <Callout tone="info"><strong>跨厂商参数对齐。</strong>Nexa 会把上述统一参数自动映射到各厂商的原生字段。若某模型不支持某参数，网关会安全地忽略而非报错。</Callout>
      </Section>

      <Section>
        <H2 id="response">响应</H2>
        <p>非流式请求返回一个 <code>chat.completion</code> 对象。主要字段：</p>
        <ParamTable rows={[
          { name: 'id', type: 'string', desc: '本次补全的唯一标识。' },
          { name: 'object', type: 'string', desc: <>固定为 <code>chat.completion</code>。</> },
          { name: 'created', type: 'integer', desc: '创建时间的 Unix 时间戳（秒）。' },
          { name: 'model', type: 'string', desc: '实际服务本次请求的模型 ID。' },
          { name: 'choices', type: 'array', desc: <>补全结果数组，含 <code>index</code>、<code>message</code>、<code>finish_reason</code>。</> },
          { name: 'usage', type: 'object', desc: <>token 用量：<code>prompt_tokens</code>、<code>completion_tokens</code>、<code>total_tokens</code>。</> },
        ]} />
        <CodeBlock panes={RESP} />
      </Section>

      <Section>
        <H2 id="stream">流式响应</H2>
        <p>当请求中设置 <code>{'"stream": true'}</code> 时，网关以 <strong>Server-Sent Events（SSE）</strong> 协议逐块推送增量。每个事件以 <code>data:</code> 开头，增量内容位于 <code>choices[0].delta.content</code>。流以 <code>data: [DONE]</code> 结束。</p>
        <CodeBlock panes={STREAM} />
        <p>SSE 数据流的原始格式如下：</p>
        <CodeBlock panes={SSE} />
        <Callout tone="warn"><strong>流式下没有 usage 字段。</strong>若需统计，请在请求中加入 <code>&quot;stream_options&quot;: &#123;&quot;include_usage&quot;: true&#125;</code>，网关会在 <code>[DONE]</code> 前补发一个仅含 <code>usage</code> 的 chunk。</Callout>
      </Section>
    </DocsShell>
  );
}
