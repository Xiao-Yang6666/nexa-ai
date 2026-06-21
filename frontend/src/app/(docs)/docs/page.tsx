import type { Metadata } from 'next';
import {
  Breadcrumb,
  Callout,
  CodeBlock,
  DocsShell,
  H2,
  H3,
  InlineLink,
  Lede,
  PageTitle,
  Section,
  Step,
  Steps,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '快速开始 · Nexa·AI 文档',
  description:
    'Nexa·AI 是一个与 OpenAI 完全兼容的 AI API 网关。本指南带你在五分钟内发出第一个请求。',
};

/* 本页 ToC（与正文 h2/h3 id 对齐）。 */
const TOC: TocItem[] = [
  { id: 'intro', label: '简介', level: 2 },
  { id: 'steps', label: '四步上手', level: 2 },
  { id: 'step-key', label: '注册并获取 Key', level: 3 },
  { id: 'step-base', label: '设置 base_url', level: 3 },
  { id: 'step-request', label: '发出第一个请求', level: 3 },
  { id: 'step-response', label: '查看响应', level: 3 },
  { id: 'next', label: '下一步', level: 2 },
];

/* 第三步「发出第一个请求」的四语言代码（curl/Python/Node/Go），与原型 1:1 对齐。 */
const FIRST_REQUEST_PANES: CodePane[] = [
  {
    lang: 'curl',
    label: 'curl',
    code: `curl https://api.nexa.ai/v1/chat/completions \\
  -H "Authorization: Bearer $NEXA_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "gpt-4o-mini",
    "messages": [{"role": "user", "content": "用一句话介绍 Nexa·AI"}]
  }'`,
  },
  {
    lang: 'python',
    label: 'Python',
    code: `from openai import OpenAI

client = OpenAI(
    base_url="https://api.nexa.ai/v1",
    api_key="sk-nexa-...",
)

resp = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "用一句话介绍 Nexa·AI"}],
)
print(resp.choices[0].message.content)`,
  },
  {
    lang: 'node',
    label: 'Node',
    code: `import OpenAI from "openai";

const client = new OpenAI({
  baseURL: "https://api.nexa.ai/v1",
  apiKey: process.env.NEXA_API_KEY,
});

const resp = await client.chat.completions.create({
  model: "gpt-4o-mini",
  messages: [{ role: "user", content: "用一句话介绍 Nexa·AI" }],
});
console.log(resp.choices[0].message.content);`,
  },
  {
    lang: 'go',
    label: 'Go',
    code: `package main

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
        Model: "gpt-4o-mini",
        Messages: []openai.ChatCompletionMessage{{
            Role: openai.ChatMessageRoleUser,
            Content: "用一句话介绍 Nexa·AI",
        }},
    })
    fmt.Println(resp.Choices[0].Message.Content)
}`,
  },
];

/* 响应 JSON 示例（单语言面板）。 */
const RESPONSE_JSON_PANES: CodePane[] = [
  {
    lang: 'json',
    label: '响应 JSON',
    code: `{
  "id": "chatcmpl-9a7f2c1e",
  "object": "chat.completion",
  "created": 1718857200,
  "model": "gpt-4o-mini",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "Nexa·AI 是一个 OpenAI 兼容的统一 AI API 网关。"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 18,
    "completion_tokens": 21,
    "total_tokens": 39
  }
}`,
  },
];

/** /docs 路由：快速开始页。S6 原型 quickstart.html 的工程化版本。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="入门" page="快速开始" />
      <PageTitle>快速开始</PageTitle>
      <Lede>
        Nexa·AI 是一个与 OpenAI 完全兼容的 AI API 网关。用一个 API Key 即可直连
        OpenAI、Anthropic、Google、DeepSeek、Qwen 等多家模型，无需为每家厂商单独申请凭证或适配接口。本指南带你在五分钟内发出第一个请求。
      </Lede>

      <Section>
        <H2 id="intro">简介</H2>
        <p>
          Nexa 在协议层完全对齐 OpenAI 的 <code>/v1/chat/completions</code>、
          <code>/v1/models</code>、<code>/v1/embeddings</code> 等接口。这意味着任何已有的
          OpenAI SDK 或代码，只需把 <strong>base URL</strong> 指向 Nexa，再换上 Nexa
          颁发的 Key，即可无缝切换，无需改动业务逻辑。
        </p>
        <ul>
          <li>
            <strong>统一入口</strong>：一个 <code>https://api.nexa.ai/v1</code>{' '}
            端点访问所有模型。
          </li>
          <li>
            <strong>统一计费</strong>：跨厂商用量、额度、限流在一处管理。
          </li>
          <li>
            <strong>零迁移成本</strong>：兼容 OpenAI SDK，改一行 base_url 即可。
          </li>
        </ul>
      </Section>

      <Section>
        <H2 id="steps">四步上手</H2>

        <Steps>
          <Step>
            <H3 id="step-key">注册并获取 API Key</H3>
            <p>
              在控制台{' '}
              <InlineLink href="/dashboard">Dashboard 的 API Keys</InlineLink>{' '}
              创建一个密钥。Nexa 颁发的 Key 形如 <code>sk-nex...xxxx</code>，请妥善保管，仅在服务端使用。
            </p>
          </Step>

          <Step>
            <H3 id="step-base">设置 base_url</H3>
            <p>把客户端的请求基址指向 Nexa 网关：</p>
            <CodeBlock
              panes={[
                {
                  lang: 'text',
                  label: 'base_url',
                  code: 'https://api.nexa.ai/v1',
                },
              ]}
            />
          </Step>

          <Step>
            <H3 id="step-request">发出第一个请求</H3>
            <p>
              下面以 <code>chat/completions</code> 为例，四种语言任选其一：
            </p>
            <CodeBlock panes={FIRST_REQUEST_PANES} />
          </Step>

          <Step>
            <H3 id="step-response">查看响应</H3>
            <p>
              请求成功后会返回一个标准的 OpenAI 兼容 JSON 对象，
              <code>choices[0].message.content</code> 即为模型回复：
            </p>
            <CodeBlock panes={RESPONSE_JSON_PANES} />
          </Step>
        </Steps>

        <Callout tone="info">
          <strong>兼容 OpenAI SDK。</strong>你无需安装任何 Nexa 专用包。任何已经使用
          OpenAI 官方 SDK 的项目，只要把 <code>base_url</code> 改为{' '}
          <code>https://api.nexa.ai/v1</code> 并替换为 Nexa 的 Key 即可立即生效。
        </Callout>
      </Section>

      <Section>
        <H2 id="next">下一步</H2>
        <ul>
          <li>
            阅读 <InlineLink href="/docs/authentication">认证说明</InlineLink>{' '}
            了解 Key 的权限与限额。
          </li>
          <li>
            查看{' '}
            <InlineLink href="/docs/chat-completions">聊天补全 API</InlineLink>{' '}
            完整参数与流式响应。
          </li>
          <li>
            浏览 <InlineLink href="/docs/models">模型列表 API</InlineLink>{' '}
            获取所有可用模型。
          </li>
        </ul>
      </Section>
    </DocsShell>
  );
}
