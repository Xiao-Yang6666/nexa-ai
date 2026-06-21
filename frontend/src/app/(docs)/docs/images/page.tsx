import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, Endpoint, H2,
  InlineLink, Lede, PageTitle, ParamTable, Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '图像 API · Nexa·AI 文档',
  description: '根据文字提示生成或编辑图像。兼容 OpenAI Images 协议。',
};

const TOC: TocItem[] = [
  { id: 'endpoint', label: '端点', level: 2 },
  { id: 'request', label: '请求示例', level: 2 },
  { id: 'params', label: '请求参数', level: 2 },
  { id: 'response', label: '响应', level: 2 },
];

const REQ: CodePane[] = [
  { lang: 'curl', label: 'curl', code: `curl https://api.nexa.ai/v1/images/generations \\
  -H "Authorization: Bearer $NEXA_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "dall-e-3",
    "prompt": "一只戴太阳镜的橘猫在海边冲浪，像素画风格",
    "size": "1024x1024",
    "n": 1
  }'` },
  { lang: 'python', label: 'Python', code: `from openai import OpenAI

client = OpenAI(
    base_url="https://api.nexa.ai/v1",
    api_key="sk-nexa-...",
)
resp = client.images.generate(
    model="dall-e-3",
    prompt="一只戴太阳镜的橘猫在海边冲浪，像素画风格",
    size="1024x1024",
    n=1,
)
print(resp.data[0].url)` },
  { lang: 'node', label: 'Node', code: `import OpenAI from "openai";

const client = new OpenAI({
  baseURL: "https://api.nexa.ai/v1",
  apiKey: process.env.NEXA_API_KEY,
});
const resp = await client.images.generate({
  model: "dall-e-3",
  prompt: "一只戴太阳镜的橘猫在海边冲浪，像素画风格",
  size: "1024x1024",
  n: 1,
});
console.log(resp.data[0].url);` },
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

    resp, _ := client.CreateImage(context.Background(), openai.ImageRequest{
        Model:  "dall-e-3",
        Prompt: "一只戴太阳镜的橘猫在海边冲浪，像素画风格",
        Size:   openai.CreateImageSize1024x1024,
        N:      1,
    })
    fmt.Println(resp.Data[0].URL)
}` },
];

const RESP: CodePane[] = [{ lang: 'json', label: '响应 JSON', code: `{
  "created": 1718860200,
  "data": [{
    "url": "https://cdn.nexa.ai/images/gen-abc123.png",
    "revised_prompt": "A pixel-art style orange cat wearing sunglasses surfing at a beach..."
  }]
}` }];

/** /docs/images：图像 API 页。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="API 参考" page="图像 API" />
      <PageTitle>图像 API</PageTitle>
      <Lede>根据文字提示生成图像，或对已有图像做编辑和变体。兼容 OpenAI Images 协议。</Lede>

      <Section>
        <H2 id="endpoint">端点</H2>
        <Endpoint method="POST" path="https://api.nexa.ai/v1/images/generations" />
        <p>需在 <code>Authorization</code> 头携带有效的 Bearer Token，详见 <InlineLink href="/docs/authentication">认证说明</InlineLink>。</p>
      </Section>

      <Section>
        <H2 id="request">请求示例</H2>
        <CodeBlock panes={REQ} />
      </Section>

      <Section>
        <H2 id="params">请求参数</H2>
        <ParamTable rows={[
          { name: 'model', type: 'string', required: true, desc: <>图像模型 ID，如 <code>dall-e-3</code>。</> },
          { name: 'prompt', type: 'string', required: true, desc: '生图提示文字，最大长度 4000 字符。描述越详细，生成结果越贴近。' },
          { name: 'n', type: 'integer', desc: '生成图片数量，默认 1。dall-e-3 固定为 1。' },
          { name: 'size', type: 'string', desc: <>图像尺寸：<code>256x256</code>、<code>512x512</code>、<code>1024x1024</code>（默认）、<code>1792x1024</code>、<code>1024x1792</code>。</> },
          { name: 'quality', type: 'string', desc: <>质量级别：<code>standard</code>（默认）或 <code>hd</code>。仅 dall-e-3 支持 hd。</> },
          { name: 'style', type: 'string', desc: <>风格：<code>vivid</code>（默认，生动）或 <code>natural</code>（自然写实）。仅 dall-e-3 支持。</> },
          { name: 'response_format', type: 'string', desc: <>返回格式：<code>url</code>（默认，临时链接）或 <code>b64_json</code>（base64 内嵌）。</> },
          { name: 'user', type: 'string', desc: '代表终端用户的稳定标识，用于滥用监测。' },
        ]} />
        <Callout tone="warn">
          <strong>临时链接有效期。</strong>默认返回的 <code>url</code> 有效期为 1 小时，请及时下载或使用 <code>b64_json</code> 格式持久存储。
        </Callout>
      </Section>

      <Section>
        <H2 id="response">响应</H2>
        <ParamTable rows={[
          { name: 'created', type: 'integer', desc: '生成时间的 Unix 时间戳。' },
          { name: 'data', type: 'array', desc: '图像对象数组，每项含 url 或 b64_json。' },
          { name: 'data[].url', type: 'string', desc: '图像临时访问 URL（response_format=url 时返回）。' },
          { name: 'data[].b64_json', type: 'string', desc: '图像 base64 编码（response_format=b64_json 时返回）。' },
          { name: 'data[].revised_prompt', type: 'string', desc: 'dall-e-3 实际使用的优化后提示（仅 dall-e-3 返回）。' },
        ]} />
        <CodeBlock panes={RESP} />
      </Section>
    </DocsShell>
  );
}
