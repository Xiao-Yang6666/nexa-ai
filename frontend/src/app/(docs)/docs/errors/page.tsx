import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, H2,
  InlineLink, Lede, PageTitle, DocTable, Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '错误码参考 · Nexa·AI 文档',
  description: '错误响应结构、HTTP 状态码与业务错误码大全。帮你实现健壮的错误处理逻辑。',
};

const TOC: TocItem[] = [
  { id: 'overview', label: '概览', level: 2 },
  { id: 'structure', label: '错误结构', level: 2 },
  { id: 'http', label: 'HTTP 状态码', level: 2 },
  { id: 'codes', label: '业务错误码', level: 2 },
  { id: 'handling', label: '处理建议', level: 2 },
];

const ERR_JSON: CodePane[] = [{ lang: 'json', label: '错误响应 JSON', code: `{
  "error": {
    "message": "You exceeded your current quota, please check your plan and billing details.",
    "type": "insufficient_quota",
    "code": "insufficient_quota",
    "param": null
  }
}` }];

/** HTTP 状态徽章（用 .badge .b-dan / .b-warn 继承设计系统样式）。 */
function StatusBadge({ code, variant }: { code: number; variant: 'danger' | 'warning' }) {
  return <span className={`badge b-${variant === 'danger' ? 'dan' : 'warn'}`}>{code}</span>;
}

/** /docs/errors：错误码参考页。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="运行约定" page="错误码参考" />
      <PageTitle>错误码参考</PageTitle>
      <Lede>
        Nexa·AI 沿用标准 HTTP 状态码语义，并在响应体中返回结构化的错误对象。本页列出全部状态码、业务错误码及其常见原因与处理建议，帮助你在客户端实现健壮的错误处理。
      </Lede>

      <Section>
        <H2 id="overview">概览</H2>
        <p>
          所有错误响应均以非 2xx 的 HTTP 状态码返回，响应体为统一的 JSON 结构。状态码大类含义如下：<code>2xx</code>{' '}
          成功；<code>4xx</code> 客户端错误（请求本身有问题，重试通常无效，需修正后再发）；<code>5xx</code>{' '}
          服务端错误（网关或上游临时故障，可按退避策略重试）。
        </p>
      </Section>

      <Section>
        <H2 id="structure">错误结构</H2>
        <p>
          错误响应体始终包含一个 <code>error</code> 对象，字段含义：<code>message</code>{' '}
          人类可读的描述；<code>type</code> 错误大类；<code>code</code> 机器可读的业务错误码；<code>param</code>{' '}
          触发错误的具体参数（若适用）。
        </p>
        <CodeBlock panes={ERR_JSON} />
      </Section>

      <Section>
        <H2 id="http">HTTP 状态码</H2>
        <p>下表覆盖网关可能返回的全部 HTTP 状态码及其典型语义：</p>
        <DocTable
          head={['状态', '含义', '常见原因', '处理建议']}
          rows={[
            [<StatusBadge key="b" code={400} variant="danger" />, 'Bad Request', '请求体非法、缺必填字段、参数取值越界。', <span key="d">检查 <code>error.param</code> 指出的字段并修正后重发。</span>],
            [<StatusBadge key="b" code={401} variant="danger" />, 'Unauthorized', '缺少 / 无效 / 已撤销的 API Key。', <span key="d">核对 <code>Authorization</code> 头与 Key 是否仍有效。</span>],
            [<StatusBadge key="b" code={403} variant="danger" />, 'Forbidden', 'Key 无该模型 / 该接口的访问权限。', '在控制台为分组授予对应模型权限。'],
            [<StatusBadge key="b" code={404} variant="danger" />, 'Not Found', '端点路径错误，或模型 ID 不存在。', <span key="d">核对 URL 与 <code>model</code>，参见 <InlineLink href="/docs/models">模型列表</InlineLink>。</span>],
            [<StatusBadge key="b" code={429} variant="warning" />, 'Too Many Requests', '触发 RPM / TPM 限流，或额度已用尽。', <span key="d">读取 <code>Retry-After</code> 退避重试，见 <InlineLink href="/docs/rate-limits">限流说明</InlineLink>。</span>],
            [<StatusBadge key="b" code={500} variant="warning" />, 'Internal Server Error', '网关内部异常。', '指数退避重试；持续出现请联系支持。'],
            [<StatusBadge key="b" code={502} variant="warning" />, 'Bad Gateway', '上游模型供应商返回了无效响应。', '稍后退避重试，或切换备用模型。'],
            [<StatusBadge key="b" code={503} variant="warning" />, 'Service Unavailable', '上游过载或维护中，暂时不可用。', <span key="d">按 <code>Retry-After</code> 退避重试。</span>],
          ]}
        />
      </Section>

      <Section>
        <H2 id="codes">业务错误码</H2>
        <p>
          <code>error.code</code> 提供比 HTTP 状态码更细的机器可读分类，便于在代码中做精准分支处理：
        </p>
        <DocTable
          head={['状态', 'error.code', '含义', '处理建议']}
          rows={[
            [<StatusBadge key="b" code={401} variant="danger" />, <code key="c" className="pn">invalid_api_key</code>, '提供的 API Key 无效或格式错误。', '重新生成 Key 并更新环境变量。'],
            [<StatusBadge key="b" code={403} variant="danger" />, <code key="c" className="pn">model_access_denied</code>, '当前 Key 未被授权访问该模型。', '在分组权限中开启该模型。'],
            [<StatusBadge key="b" code={404} variant="danger" />, <code key="c" className="pn">model_not_found</code>, <span key="m">请求的 <code>model</code> 不存在或已下线。</span>, <span key="d">改用 <InlineLink href="/docs/models">模型列表</InlineLink> 中的有效 ID。</span>],
            [<StatusBadge key="b" code={400} variant="danger" />, <code key="c" className="pn">invalid_request_error</code>, '请求结构或字段取值不合法。', <span key="d">依据 <code>message</code> 与 <code>param</code> 修正。</span>],
            [<StatusBadge key="b" code={400} variant="danger" />, <code key="c" className="pn">context_length_exceeded</code>, '输入 token 超出模型上下文窗口。', '缩短输入或改用更大上下文的模型。'],
            [<StatusBadge key="b" code={400} variant="danger" />, <code key="c" className="pn">content_policy_violation</code>, '提示词或输出触发内容安全策略。', '调整内容后重试。'],
            [<StatusBadge key="b" code={429} variant="warning" />, <code key="c" className="pn">rate_limit_exceeded</code>, '超出每分钟请求或 token 速率上限。', <span key="d">按 <code>Retry-After</code> 退避，降低并发。</span>],
            [<StatusBadge key="b" code={429} variant="warning" />, <code key="c" className="pn">insufficient_quota</code>, '账户额度已用尽或欠费。', '前往控制台充值或调整套餐。'],
            [<StatusBadge key="b" code={502} variant="warning" />, <code key="c" className="pn">upstream_error</code>, '上游供应商返回错误。', '退避重试，或在请求中指定备用模型。'],
            [<StatusBadge key="b" code={503} variant="warning" />, <code key="c" className="pn">engine_overloaded</code>, '上游引擎当前过载。', '稍后重试或降低请求频率。'],
          ]}
        />
      </Section>

      <Section>
        <H2 id="handling">处理建议</H2>
        <Callout tone="info">
          <strong>4xx 不要盲目重试。</strong>除 <code>429</code> 外的客户端错误（<code>400/401/403/404</code>）通常源于请求本身的问题，原样重发只会再次失败。应先依据 <code>error.code</code> 与 <code>message</code> 修正请求，再重新提交。
        </Callout>
        <Callout tone="warn">
          <strong>5xx 与 429 才适合重试。</strong>对 <code>429/500/502/503</code> 采用指数退避（含抖动）重试，并尊重响应中的 <code>Retry-After</code> 头；设置最大重试次数以避免雪崩。具体退避策略与示例代码见{' '}
          <InlineLink href="/docs/rate-limits">限流说明</InlineLink>。
        </Callout>
        <Callout tone="info">
          <strong>始终以 <code>error.code</code> 为分支依据。</strong><code>message</code> 文案可能随版本调整，而 <code>code</code>{' '}
          是稳定契约。在代码中按 <code>code</code> 做精确匹配，能让错误处理在网关升级后依然可靠。
        </Callout>
      </Section>
    </DocsShell>
  );
}
