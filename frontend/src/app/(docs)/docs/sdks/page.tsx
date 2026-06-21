import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, H2,
  InlineLink, Lede, PageTitle, Section,
} from '@/features/docs';
import type { TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: 'SDK 下载 · Nexa·AI 文档',
  description: 'Nexa·AI 兼容 OpenAI SDK，无需专用包。各语言安装方式与文档入口。',
};

const TOC: TocItem[] = [
  { id: 'compat', label: '兼容说明', level: 2 },
  { id: 'official', label: '官方 SDK', level: 2 },
  { id: 'community', label: '社区 SDK', level: 2 },
];

/** /docs/sdks：SDK 下载页。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="资源" page="SDK 下载" />
      <PageTitle>SDK 下载</PageTitle>
      <Lede>
        Nexa·AI 完全兼容 OpenAI 的 HTTP 协议，因此<strong>无需任何专用 SDK</strong>——直接使用各语言的 OpenAI 官方客户端，只把 <code>base_url</code> 指向 Nexa 即可。下面汇总各语言的安装方式与文档入口。
      </Lede>

      <Section>
        <H2 id="compat">兼容说明</H2>
        <p>
          所有官方 OpenAI SDK 都暴露 <code>base_url</code>（或 <code>baseURL</code> / <code>BaseURL</code>）配置项。把它改为 <code>https://api.nexa.ai/v1</code>，并填入你的 Nexa API Key，原有代码几乎无需改动即可跨厂商调用全部模型。
        </p>
        <Callout tone="info">
          <strong>唯一要改的就是两行配置。</strong>把 <code>base_url</code> 设为 <code>https://api.nexa.ai/v1</code>、<code>api_key</code> 设为你的 <code>sk-nexa-...</code> Key，其余调用方式与官方完全一致。详见 <InlineLink href="/docs">快速开始</InlineLink>。
        </Callout>
      </Section>

      <Section>
        <H2 id="official">官方 SDK</H2>
        <div className="sdk-grid">

          {/* Python */}
          <div className="sdk-card">
            <div className="sdk-head">
              <span className="sdk-ic">
                <svg viewBox="0 0 24 24">
                  <path d="M8 3h4a3 3 0 0 1 3 3v3H9a3 3 0 0 0-3 3v2H5a2 2 0 0 1-2-2V6a3 3 0 0 1 3-3Z" />
                  <path d="M16 21h-4a3 3 0 0 1-3-3v-3h6a3 3 0 0 0 3-3v-2h1a2 2 0 0 1 2 2v6a3 3 0 0 1-3 3Z" />
                </svg>
              </span>
              <div>
                <div className="sdk-name">Python</div>
                <div className="sdk-pkg">openai</div>
              </div>
            </div>
            <p className="sdk-desc">OpenAI 官方 Python 客户端，同步/异步均支持，自动重试与流式解析开箱即用。</p>
            <CodeBlock panes={[{ lang: 'bash', label: '安装', code: 'pip install openai' }]} />
            <div className="sdk-foot">
              <a className="sdk-doc" href="https://github.com/openai/openai-python" target="_blank" rel="noopener noreferrer">
                查看文档
                <svg viewBox="0 0 24 24"><path d="M7 17 17 7M9 7h8v8" /></svg>
              </a>
            </div>
          </div>

          {/* Node.js */}
          <div className="sdk-card">
            <div className="sdk-head">
              <span className="sdk-ic">
                <svg viewBox="0 0 24 24">
                  <path d="m4 6 8 4 8-4M12 10v8M4 6v8l8 4 8-4V6l-8-4-8 4Z" />
                </svg>
              </span>
              <div>
                <div className="sdk-name">Node.js</div>
                <div className="sdk-pkg">openai</div>
              </div>
            </div>
            <p className="sdk-desc">官方 TypeScript/JavaScript 客户端，类型完备，支持流式与浏览器/边缘运行时。</p>
            <CodeBlock panes={[{ lang: 'bash', label: '安装', code: 'npm install openai' }]} />
            <div className="sdk-foot">
              <a className="sdk-doc" href="https://github.com/openai/openai-node" target="_blank" rel="noopener noreferrer">
                查看文档
                <svg viewBox="0 0 24 24"><path d="M7 17 17 7M9 7h8v8" /></svg>
              </a>
            </div>
          </div>

          {/* Go */}
          <div className="sdk-card">
            <div className="sdk-head">
              <span className="sdk-ic">
                <svg viewBox="0 0 24 24">
                  <ellipse cx="12" cy="12" rx="9" ry="5" />
                  <path d="M3 12c0 2.8 4 5 9 5s9-2.2 9-5" />
                </svg>
              </span>
              <div>
                <div className="sdk-name">Go</div>
                <div className="sdk-pkg">sashabaranov/go-openai</div>
              </div>
            </div>
            <p className="sdk-desc">社区广泛使用的 Go 客户端，类型安全，支持所有 OpenAI 兼容接口。</p>
            <CodeBlock panes={[{ lang: 'bash', label: '安装', code: 'go get github.com/sashabaranov/go-openai' }]} />
            <div className="sdk-foot">
              <a className="sdk-doc" href="https://github.com/sashabaranov/go-openai" target="_blank" rel="noopener noreferrer">
                查看文档
                <svg viewBox="0 0 24 24"><path d="M7 17 17 7M9 7h8v8" /></svg>
              </a>
            </div>
          </div>

          {/* Java */}
          <div className="sdk-card">
            <div className="sdk-head">
              <span className="sdk-ic">
                <svg viewBox="0 0 24 24">
                  <path d="M8 2a7 7 0 0 1 0 14h8a7 7 0 0 0 0-14H8Z" />
                  <path d="M8 16v4M16 16v4" />
                </svg>
              </span>
              <div>
                <div className="sdk-name">Java</div>
                <div className="sdk-pkg">openai-java</div>
              </div>
            </div>
            <p className="sdk-desc">OpenAI 官方 Java 客户端，支持同步/异步模式与流式输出。</p>
            <CodeBlock panes={[{ lang: 'xml', label: 'Maven', code: `<dependency>
  <groupId>com.openai</groupId>
  <artifactId>openai-java</artifactId>
  <version>LATEST</version>
</dependency>` }]} />
            <div className="sdk-foot">
              <a className="sdk-doc" href="https://github.com/openai/openai-java" target="_blank" rel="noopener noreferrer">
                查看文档
                <svg viewBox="0 0 24 24"><path d="M7 17 17 7M9 7h8v8" /></svg>
              </a>
            </div>
          </div>
        </div>
      </Section>

      <Section>
        <H2 id="community">社区 SDK</H2>
        <p>
          下表汇总了支持自定义 <code>base_url</code> 的主流社区客户端。它们均可直连 Nexa·AI，按语言偏好选用即可：
        </p>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>语言</th>
                <th>包名</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr><td>Rust</td><td className="pn">async-openai</td><td>异步 Rust 客户端，tokio 驱动，支持流式。</td></tr>
              <tr><td>C# / .NET</td><td className="pn">Azure.AI.OpenAI</td><td>微软官方，支持 OpenAI 端点与自定义 baseUrl。</td></tr>
              <tr><td>PHP</td><td className="pn">openai-php/client</td><td>PHP 8+ 同步客户端。</td></tr>
              <tr><td>Ruby</td><td className="pn">ruby-openai</td><td>支持同步/流式，兼容 OpenAI 协议。</td></tr>
            </tbody>
          </table>
        </div>
        <Callout tone="warn">
          <strong>版本兼容性。</strong>Nexa 对齐 OpenAI API <code>v1</code> 规范；各社区库如有版本特有字段不兼容时，以 <InlineLink href="/docs/changelog">更新日志</InlineLink> 说明为准。
        </Callout>
      </Section>
    </DocsShell>
  );
}
