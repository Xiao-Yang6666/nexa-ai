import type { Metadata } from 'next';
import {
  Breadcrumb, Callout, CodeBlock, DocsShell, H2,
  Lede, PageTitle, DocTable, Section,
} from '@/features/docs';
import type { CodePane, TocItem } from '@/features/docs';

export const metadata: Metadata = {
  title: '限流说明 · Nexa·AI 文档',
  description: '限流维度（RPM/TPM）、额度档位、429 响应与推荐的退避重试策略。',
};

const TOC: TocItem[] = [
  { id: 'mechanism', label: '限流机制', level: 2 },
  { id: 'tiers', label: '额度档位', level: 2 },
  { id: 'response', label: '命中限流', level: 2 },
  { id: 'backoff', label: '退避重试', level: 2 },
];

const RESP_429: CodePane[] = [{ lang: 'http', label: '429 响应', code: `HTTP/1.1 429 Too Many Requests
Retry-After: 3
x-ratelimit-remaining-requests: 0
Content-Type: application/json

{
  "error": {
    "message": "Rate limit reached for requests. Please retry after 3s.",
    "type": "rate_limit_exceeded",
    "code": "rate_limit_exceeded"
  }
}` }];

const BACKOFF: CodePane[] = [
  { lang: 'bash', label: 'curl', code: `# --retry 内置指数退避，遇 429/5xx 自动重试
curl --retry 5 --retry-delay 1 --retry-all-errors \\
  https://api.nexa.ai/v1/chat/completions \\
  -H "Authorization: Bearer $NEXA_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{"model":"gpt-4o","messages":[{"role":"user","content":"hi"}]}'` },
  { lang: 'python', label: 'Python', code: `import time, random
from openai import OpenAI, RateLimitError

client = OpenAI(base_url="https://api.nexa.ai/v1")

def with_retry(fn, max_tries=5):
    for i in range(max_tries):
        try:
            return fn()
        except RateLimitError:
            if i == max_tries - 1:
                raise
            delay = 2 ** i + random.random()
            time.sleep(delay)` },
  { lang: 'node', label: 'Node', code: `async function withRetry(fn, maxTries = 5) {
  for (let i = 0; i < maxTries; i++) {
    try {
      return await fn();
    } catch (err) {
      if (err.status !== 429 || i === maxTries - 1) throw err;
      const delay = (2 ** i + Math.random()) * 1000;
      await new Promise((r) => setTimeout(r, delay));
    }
  }
}` },
  { lang: 'go', label: 'Go', code: `func withRetry(fn func() error, maxTries int) error {
    var err error
    for i := 0; i < maxTries; i++ {
        if err = fn(); err == nil {
            return nil
        }
        if i == maxTries-1 {
            return err
        }
        backoff := time.Duration(math.Pow(2, float64(i)))*time.Second +
            time.Duration(rand.Intn(1000))*time.Millisecond
        time.Sleep(backoff)
    }
    return err
}` },
];

/** /docs/rate-limits：限流说明页。 */
export default function Page() {
  return (
    <DocsShell toc={TOC}>
      <Breadcrumb group="运行约定" page="限流说明" />
      <PageTitle>限流说明</PageTitle>
      <Lede>
        为保障服务质量，Nexa·AI 对每个 API Key 与分组施加速率限制。本页解释限流维度、各档位额度、命中限流时的响应，以及推荐的退避重试策略，帮助你在高并发下平稳运行。
      </Lede>

      <Section>
        <H2 id="mechanism">限流机制</H2>
        <p>限流按两个维度并行计量，任一维度超限都会触发 <code>429</code>：</p>
        <ul>
          <li><strong>RPM（Requests Per Minute）</strong>——每分钟最大请求数，约束调用频次。</li>
          <li><strong>TPM（Tokens Per Minute）</strong>——每分钟最大 token 吞吐（输入+输出合计），约束数据体量。</li>
        </ul>
        <p>
          限流粒度可按 <strong>API Key</strong> 或 <strong>分组（Group）</strong>设定：同一分组下的多个 Key 共享分组级配额，单个 Key 也可设独立上限。计量采用滑动窗口，窗口随时间连续推进，而非每分钟整点清零。每条响应都会回携以下头部，便于你实时感知余量：
        </p>
        <DocTable
          head={['响应头', '含义']}
          rows={[
            [<code key="c-x-ratelimit-limit-requests" className="pn">x-ratelimit-limit-requests</code>, '当前窗口的 RPM 上限。'],
            [<code key="c-x-ratelimit-remaining-requests" className="pn">x-ratelimit-remaining-requests</code>, '本窗口剩余可用请求数。'],
            [<code key="c-x-ratelimit-limit-tokens" className="pn">x-ratelimit-limit-tokens</code>, '当前窗口的 TPM 上限。'],
            [<code key="c-x-ratelimit-remaining-tokens" className="pn">x-ratelimit-remaining-tokens</code>, '本窗口剩余可用 token 数。'],
            [<code key="c-x-ratelimit-reset-requests" className="pn">x-ratelimit-reset-requests</code>, '请求配额恢复的预计秒数。'],
          ]}
        />
      </Section>

      <Section>
        <H2 id="tiers">额度档位</H2>
        <p>不同套餐对应不同的默认 RPM / TPM 上限。企业分组可在控制台申请定制额度。下表为各档位的基准值（聊天类模型）：</p>
        <DocTable
          head={['套餐 / 分组', 'RPM', 'TPM', '并发上限']}
          rows={[
            [<code key="c-Free" className="pn">Free</code>, '20', '40,000', '2'],
            [<code key="c-Starter" className="pn">Starter</code>, '200', '400,000', '10'],
            [<code key="c-Pro" className="pn">Pro</code>, '1,000', '2,000,000', '50'],
            [<code key="c-Scale" className="pn">Scale</code>, '5,000', '10,000,000', '200'],
            [<code key="c-Enterprise" className="pn">Enterprise</code>, <span key="r1" className="req-yes">定制</span>, <span key="r2" className="req-yes">定制</span>, <span key="r3" className="req-yes">定制</span>],
          ]}
        />
        <Callout tone="info">
          <strong>额度按分组聚合。</strong>若一个分组内有多个 Key，它们共享上表的分组级配额。需要隔离配额时，请为关键业务创建独立分组，避免相互挤占。
        </Callout>
      </Section>

      <Section>
        <H2 id="response">命中限流</H2>
        <p>
          超出 RPM 或 TPM 时，网关返回 <code>429 Too Many Requests</code>，并在 <code>Retry-After</code> 头中给出建议的等待秒数。响应体携带 <code>rate_limit_exceeded</code> 错误码。
        </p>
        <CodeBlock panes={RESP_429} />
      </Section>

      <Section>
        <H2 id="backoff">退避重试</H2>
        <p>
          最佳实践是<strong>指数退避（exponential backoff）叠加随机抖动（jitter）</strong>：每次失败后等待时间翻倍并加入随机量，避免多客户端同步重试造成尖峰。优先尊重 <code>Retry-After</code> 头给出的值，并设置最大重试次数。
        </p>
        <CodeBlock panes={BACKOFF} />
        <Callout tone="warn">
          <strong>务必设上限。</strong>无限重试会在上游持续故障时放大压力。建议最大重试 5 次、单次最长等待 30s，超过后向上抛出错误交由业务降级处理。
        </Callout>
      </Section>
    </DocsShell>
  );
}
