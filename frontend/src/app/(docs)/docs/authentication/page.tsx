import type { Metadata } from 'next';
import {
  Breadcrumb,
  Callout,
  CodeBlock,
  DocsShell,
  H2,
  InlineLink,
  Lede,
  PageTitle,
  ParamTable,
  Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '认证说明 · Nexa·AI 文档',
  description:
    'Nexa·AI 使用 API Key（Bearer Token）进行身份认证。本页说明 Key 的获取、使用、权限模型与安全实践。',
};

const TOC: TocItem[] = [
  { id: 'keys', label: 'API Key 机制', level: 2 },
  { id: 'header', label: '在请求头携带 Key', level: 2 },
  { id: 'scope', label: '权限与限额', level: 2 },
  { id: 'security', label: '安全建议', level: 2 },
];

const HEADER_PANES: CodePane[] = [
  {
    lang: 'curl',
    label: 'curl',
    code: `# 推荐用环境变量保存 Key，避免写入代码
export NEXA_API_KEY="sk-nexa-..."

curl https://api.nexa.ai/v1/models \\
  -H "Authorization: Bearer $NEXA_API_KEY"`,
  },
  {
    lang: 'python',
    label: 'Python',
    code: `import os
from openai import OpenAI

# 从环境变量读取，切勿硬编码到源码
client = OpenAI(
    base_url="https://api.nexa.ai/v1",
    api_key=os.environ["NEXA_API_KEY"],
)`,
  },
  {
    lang: 'node',
    label: 'Node',
    code: `import OpenAI from "openai";

// 从 process.env 读取，绝不写入前端代码
const client = new OpenAI({
  baseURL: "https://api.nexa.ai/v1",
  apiKey: process.env.NEXA_API_KEY,
});`,
  },
  {
    lang: 'go',
    label: 'Go',
    code: `package main

import (
    "os"

    "github.com/sashabaranov/go-openai"
)

func newClient() *openai.Client {
    cfg := openai.DefaultConfig(os.Getenv("NEXA_API_KEY"))
    cfg.BaseURL = "https://api.nexa.ai/v1"
    return openai.NewClientWithConfig(cfg)
}`,
  },
];

const UNAUTHORIZED_PANES: CodePane[] = [
  {
    lang: 'json',
    label: '401 响应',
    code: `{
  "error": {
    "message": "Incorrect API key provided.",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}`,
  },
];

/** /docs/authentication 路由：认证说明页。S6 原型 authentication.html 工程化版本。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="入门" page="认证说明" />
      <PageTitle>认证说明</PageTitle>
      <Lede>
        Nexa·AI 使用 API Key 进行身份认证。所有对{' '}
        <code>https://api.nexa.ai/v1</code> 的请求都必须在 HTTP 请求头中携带有效的
        Bearer Token。本页说明 Key 的获取、使用、权限模型与安全实践。
      </Lede>

      <Section>
        <H2 id="keys">API Key 机制</H2>
        <p>
          在控制台创建的每个 API Key 形如 <code>sk-nex...xxxx</code>。Key
          一旦生成，完整明文仅展示一次，请立即妥善保存。Nexa
          仅存储其哈希值，无法二次找回——遗失只能吊销重建。
        </p>
        <ul>
          <li>每个账户可同时持有多个 Key，便于按环境（生产 / 测试）或按服务隔离。</li>
          <li>Key 可随时在控制台吊销，吊销后立即失效。</li>
          <li>建议为每个 Key 配置标签与到期时间，便于审计与轮换。</li>
        </ul>
      </Section>

      <Section>
        <H2 id="header">在请求头携带 Key</H2>
        <p>
          认证方式为标准的 HTTP Bearer Token。在每个请求的{' '}
          <code>Authorization</code> 头中传入 <code>Bearer</code> 前缀加你的 Key：
        </p>
        <CodeBlock panes={HEADER_PANES} />
        <p>
          若 Key 缺失、格式错误或已吊销，网关将返回{' '}
          <code>401 Unauthorized</code>：
        </p>
        <CodeBlock panes={UNAUTHORIZED_PANES} />
      </Section>

      <Section>
        <H2 id="scope">权限与限额</H2>
        <p>每个 API Key 可在控制台配置独立的权限范围与用量上限，便于细粒度管控：</p>
        <ParamTable
          rows={[
            {
              name: '允许模型',
              type: '—',
              desc: (
                <>
                  限制该 Key 可调用的模型族，例如仅允许 <code>gpt-*</code> 或{' '}
                  <code>claude-*</code>。
                </>
              ),
            },
            {
              name: '月度额度',
              type: '—',
              desc: '设定该 Key 的消费上限（按美元或 token 计），超出后自动拒绝请求。',
            },
            {
              name: '速率限制',
              type: '—',
              desc: (
                <>
                  每个 Key 独立的 RPM / TPM 上限，详见{' '}
                  <InlineLink href="/docs/rate-limits">限流说明</InlineLink>。
                </>
              ),
            },
            {
              name: 'IP 白名单',
              type: '—',
              desc: '可选，限定仅来自指定 IP 段的请求携带此 Key 时才被接受。',
            },
            {
              name: '到期时间',
              type: '—',
              desc: '可设定 Key 自动失效时间，过期后需手动续期或重建。',
            },
          ]}
        />
      </Section>

      <Section>
        <H2 id="security">安全建议</H2>
        <Callout tone="warn">
          <strong>切勿在前端或客户端暴露 API Key。</strong>浏览器、移动 App
          等客户端代码可被任意用户查看，一旦泄露将被滥用并产生费用。请始终在受信任的服务端发起请求，由后端代理转发，或通过
          BFF 模式中转。
        </Callout>
        <ul>
          <li>
            <strong>使用环境变量</strong>：不要把 Key
            硬编码进源码或提交到版本库；用 <code>.env</code> 配合密钥管理服务注入。
          </li>
          <li>
            <strong>定期轮换</strong>：为 Key
            设置到期时间并周期性更换；轮换时先创建新 Key、灰度切换、再吊销旧 Key。
          </li>
          <li>
            <strong>最小权限</strong>：按服务拆分多个 Key，各自仅授予必要的模型与额度。
          </li>
          <li>
            <strong>监控异常</strong>：在控制台开启用量告警，对突增的请求量或费用及时响应。
          </li>
        </ul>
      </Section>
    </DocsShell>
  );
}
