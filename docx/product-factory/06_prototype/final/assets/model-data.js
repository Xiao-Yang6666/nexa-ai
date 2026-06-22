/**
 * Nexa·AI 模型广场 — 模型数据表
 * ------------------------------------------------------------------
 * 数据来源说明：
 *  - 厂商图标 SVG 取自 simple-icons 开源库 (MIT 协议，可商用)。
 *  - 价格单位统一为 USD / 1M tokens，input(输入价) 与 output(输出价) 分开。
 *  - source 字段注明价格来源与核对日期。
 *  - 标注 "OpenRouter API 2026-06-20" 的为本次实时 API 抓取确认价。
 *  - 标注 "厂商官方定价" 的为各家官方 pricing 页公开发布的标准价
 *    (这些模型已从 OpenRouter 实时目录下线，采用其官方发布价，价格稳定)。
 *  - 查不到的字段置 null，source 标 "待核"。
 * ------------------------------------------------------------------
 */

/* ============ 一、厂商官方图标 ============ */
/* openai / anthropic / googlegemini / x(xAI) / meta / mistralai 来自 simple-icons 官方 SVG path。
 * DeepSeek / 通义千问(阿里) / 智谱GLM / 月之暗面 simple-icons 无收录，
 * 使用「首字母 + 品牌主色圆形」占位 (placeholder:true，仅给品牌主色 hex)。 */
window.VENDOR_ICONS = {
  'OpenAI': {
    color: '#412991',
    placeholder: false,
    svg: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.0557 6.0557 0 0 0 5.7718-4.2058 5.9894 5.9894 0 0 0 3.9977-2.9001 6.0557 6.0557 0 0 0-.7475-7.0729zm-9.022 12.6081a4.4755 4.4755 0 0 1-2.8764-1.0408l.1419-.0804 4.7783-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.504 4.504 0 0 1-4.4945 4.4944zm-9.6607-4.1254a4.4708 4.4708 0 0 1-.5346-3.0137l.142.0852 4.783 2.7582a.7712.7712 0 0 0 .7806 0l5.8428-3.3685v2.3324a.0804.0804 0 0 1-.0332.0615L9.74 19.9502a4.4992 4.4992 0 0 1-6.1408-1.6464zM2.3408 7.8956a4.485 4.485 0 0 1 2.3655-1.9728V11.6a.7664.7664 0 0 0 .3879.6765l5.8144 3.3543-2.0201 1.1685a.0757.0757 0 0 1-.071 0l-4.8303-2.7865A4.504 4.504 0 0 1 2.3408 7.872zm16.5963 3.8558L13.1038 8.364 15.1192 7.2a.0757.0757 0 0 1 .071 0l4.8303 2.7913a4.4944 4.4944 0 0 1-.6765 8.1042v-5.6772a.79.79 0 0 0-.407-.667zm2.0107-3.0231l-.142-.0852-4.7735-2.7818a.7759.7759 0 0 0-.7854 0L9.409 9.2297V6.8974a.0662.0662 0 0 1 .0284-.0615l4.8303-2.7866a4.4992 4.4992 0 0 1 6.6802 4.66zM8.3065 12.863l-2.02-1.1638a.0804.0804 0 0 1-.038-.0567V6.0742a4.4992 4.4992 0 0 1 7.3757-3.4537l-.142.0805L8.704 5.459a.7948.7948 0 0 0-.3927.6813zm1.0976-2.3654l2.602-1.4998 2.6069 1.4998v2.9994l-2.5974 1.4997-2.6067-1.4997Z"/></svg>'
  },
  'Anthropic': {
    color: '#D97757',
    placeholder: false,
    svg: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M17.3041 3.541h-3.6718l6.696 16.918H24Zm-10.6082 0L0 20.459h3.7442l1.3693-3.5527h7.0052l1.3693 3.5528h3.7442L10.5363 3.5409Zm-.3712 10.2232 2.2914-5.9456 2.2914 5.9456Z"/></svg>'
  },
  'Google': {
    color: '#8E75B2',
    placeholder: false,
    svg: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M11.04 19.32Q12 21.51 12 24q0-2.49.93-4.68.96-2.19 2.58-3.81t3.81-2.55Q21.51 12 24 12q-2.49 0-4.68-.93a12.3 12.3 0 0 1-3.81-2.58 12.3 12.3 0 0 1-2.58-3.81Q12 2.49 12 0q0 2.49-.96 4.68-.93 2.19-2.55 3.81a12.3 12.3 0 0 1-3.81 2.58Q2.49 12 0 12q2.49 0 4.68.96 2.19.93 3.81 2.55t2.55 3.81"/></svg>'
  },
  'xAI': {
    color: '#000000',
    placeholder: false,
    svg: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M14.234 10.162 22.977 0h-2.072l-7.591 8.824L7.251 0H.258l9.168 13.343L.258 24H2.33l8.016-9.318L16.749 24h6.993zm-2.837 3.299-.929-1.329L3.076 1.56h3.182l5.965 8.532.929 1.329 7.754 11.09h-3.182z"/></svg>'
  },
  'Meta': {
    color: '#0467DF',
    placeholder: false,
    svg: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M6.915 4.03c-1.968 0-3.683 1.28-4.871 3.113C.704 9.208 0 11.883 0 14.449c0 .706.07 1.369.21 1.973a6.624 6.624 0 0 0 .265.86 5.297 5.297 0 0 0 .371.761c.696 1.159 1.818 1.927 3.593 1.927 1.497 0 2.633-.671 3.965-2.444.76-1.012 1.144-1.626 2.663-4.32l.756-1.339.186-.325c.061.1.121.196.183.3l2.152 3.595c.724 1.21 1.665 2.556 2.47 3.314 1.046.987 1.992 1.22 3.06 1.22 1.075 0 1.876-.355 2.455-.843a3.743 3.743 0 0 0 .81-.973c.542-.939.861-2.127.861-3.745 0-2.72-.681-5.357-2.084-7.45-1.282-1.912-2.957-2.93-4.716-2.93-1.047 0-2.088.467-3.053 1.308-.652.57-1.257 1.29-1.82 2.05-.69-.875-1.335-1.547-1.958-2.056-1.182-.966-2.315-1.303-3.454-1.303zm10.16 2.053c1.147 0 2.188.758 2.992 1.999 1.132 1.748 1.647 4.195 1.647 6.4 0 1.548-.368 2.9-1.839 2.9-.58 0-1.027-.23-1.664-1.004-.496-.601-1.343-1.878-2.832-4.358l-.617-1.028a44.908 44.908 0 0 0-1.255-1.98c.07-.109.141-.224.211-.327 1.12-1.667 2.118-2.602 3.358-2.602zm-10.201.553c1.265 0 2.058.791 2.675 1.446.307.327.737.871 1.234 1.579l-1.02 1.566c-.757 1.163-1.882 3.017-2.837 4.338-1.191 1.649-1.81 1.817-2.486 1.817-.524 0-1.038-.237-1.383-.794-.263-.426-.464-1.13-.464-2.046 0-2.221.63-4.535 1.66-6.088.454-.687.964-1.226 1.533-1.533a2.264 2.264 0 0 1 1.088-.285z"/></svg>'
  },
  'Mistral': {
    color: '#FA520F',
    placeholder: false,
    svg: '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M17.143 3.429v3.428h-3.429v3.429h-3.428V6.857H6.857V3.43H3.43v13.714H0v3.428h10.286v-3.428H6.857v-3.429h3.429v3.429h3.429v-3.429h3.428v3.429h-3.428v3.428H24v-3.428h-3.43V3.429z"/></svg>'
  },

  /* —— 以下 simple-icons 无官方收录，使用首字母 + 品牌主色圆形占位 —— */
  'DeepSeek':  { color: '#4D6BFE', placeholder: true, letter: 'D', svg: null },
  '阿里通义':   { color: '#615CED', placeholder: true, letter: 'Q', svg: null },  // 通义千问 Qwen
  '智谱AI':     { color: '#3859FF', placeholder: true, letter: 'G', svg: null },  // 智谱 GLM
  '月之暗面':   { color: '#16191E', placeholder: true, letter: 'K', svg: null }   // Moonshot / Kimi
};

/* ============ 二、模型价格数据 ============
 * official_in / official_out: USD per 1M tokens (真实官方价 / 实查价)
 * 价格未知字段为 null，source 标 "待核"
 */
window.MODEL_PRICING = [
  /* ---------- OpenAI ---------- */
  { name: 'gpt-4o', vendor: 'OpenAI', ctx: '128K', cats: ['chat', 'vision'],
    official_in: 2.5, official_out: 10.0,
    tags: ['对话', '多模态', '函数调用'],
    source: 'OpenRouter API 2026-06-20 (与 openai.com/pricing 官方价一致)' },
  { name: 'gpt-4o-mini', vendor: 'OpenAI', ctx: '128K', cats: ['chat', 'vision'],
    official_in: 0.15, official_out: 0.6,
    tags: ['对话', '轻量', '高性价比'],
    source: 'OpenRouter API 2026-06-20' },
  { name: 'o1', vendor: 'OpenAI', ctx: '200K', cats: ['reasoning'],
    official_in: 15.0, official_out: 60.0,
    tags: ['推理', '复杂任务'],
    source: 'OpenRouter API 2026-06-20' },
  { name: 'o1-mini', vendor: 'OpenAI', ctx: '128K', cats: ['reasoning'],
    official_in: 1.1, official_out: 4.4,
    tags: ['推理', '轻量'],
    source: 'openai.com/pricing 官方价 (OR 实时目录已下线) 2026-06' },
  { name: 'o3-mini', vendor: 'OpenAI', ctx: '200K', cats: ['reasoning'],
    official_in: 1.1, official_out: 4.4,
    tags: ['推理', '轻量', '高性价比'],
    source: 'OpenRouter API 2026-06-20' },

  /* ---------- Anthropic ---------- */
  { name: 'claude-3.5-sonnet', vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'],
    official_in: 3.0, official_out: 15.0,
    tags: ['对话', '代码', '多模态'],
    source: 'anthropic.com/pricing 官方价 (OR 实时目录已下线) 2026-06' },
  { name: 'claude-3.5-haiku', vendor: 'Anthropic', ctx: '200K', cats: ['chat'],
    official_in: 0.8, official_out: 4.0,
    tags: ['对话', '轻量', '快速'],
    source: 'OpenRouter API 2026-06-20' },
  { name: 'claude-3-opus', vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision'],
    official_in: 15.0, official_out: 75.0,
    tags: ['对话', '多模态', '高质量'],
    source: 'anthropic.com/pricing 官方价 (OR 实时目录已下线) 2026-06' },

  /* —— opus-4.8 家族：品质分级样板（同族三档，family 归组 / tier 品质徽章）——
     旗舰=官方满血，增强=高性价比降配，经济=入门够用。对外只用中性品质词，不暴露"满血/残血"。
     售价按品质递减；同一 family 在模型广场归组相邻展示，价格页同模型三档分行。 */
  { name: 'opus-4.8', vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'],
    family: 'opus-4.8', familyLabel: 'Claude Opus 4.8', tier: 'flagship', tierLabel: '旗舰',
    official_in: 15.0, official_out: 75.0,
    tags: ['对话', '代码', '多模态', '旗舰'],
    source: '品质分级样板 2026-06' },
  { name: 'opus-4.8-增强', vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'vision', 'code'],
    family: 'opus-4.8', familyLabel: 'Claude Opus 4.8', tier: 'enhanced', tierLabel: '增强',
    official_in: 9.0, official_out: 45.0,
    tags: ['对话', '代码', '高性价比'],
    source: '品质分级样板 2026-06' },
  { name: 'opus-4.8-经济', vendor: 'Anthropic', ctx: '200K', cats: ['chat', 'code'],
    family: 'opus-4.8', familyLabel: 'Claude Opus 4.8', tier: 'economy', tierLabel: '经济',
    official_in: 3.0, official_out: 15.0,
    tags: ['对话', '入门', '低价'],
    source: '品质分级样板 2026-06' },

  /* ---------- Google ---------- */
  { name: 'gemini-1.5-pro', vendor: 'Google', ctx: '2M', cats: ['chat', 'vision'],
    official_in: 1.25, official_out: 5.0,
    tags: ['对话', '多模态', '长上下文'],
    source: 'ai.google.dev/pricing 官方价 (≤128K档, OR 实时目录已下线) 2026-06' },
  { name: 'gemini-1.5-flash', vendor: 'Google', ctx: '1M', cats: ['chat', 'vision'],
    official_in: 0.075, official_out: 0.3,
    tags: ['对话', '多模态', '高性价比'],
    source: 'ai.google.dev/pricing 官方价 (≤128K档, OR 实时目录已下线) 2026-06' },
  { name: 'gemini-2.0-flash', vendor: 'Google', ctx: '1M', cats: ['chat', 'vision'],
    official_in: 0.1, official_out: 0.4,
    tags: ['对话', '多模态', '快速'],
    source: 'ai.google.dev/pricing 官方价 (OR 实时目录已下线) 2026-06' },

  /* ---------- xAI ---------- */
  { name: 'grok-2', vendor: 'xAI', ctx: '128K', cats: ['chat'],
    official_in: 2.0, official_out: 10.0,
    tags: ['对话', '实时知识'],
    source: 'x.ai/api 官方价 (OR 实时目录已下线) 2026-06' },
  { name: 'grok-beta', vendor: 'xAI', ctx: '128K', cats: ['chat'],
    official_in: 5.0, official_out: 15.0,
    tags: ['对话', '测试版'],
    source: 'x.ai/api 官方价 (OR 实时目录已下线) 2026-06' },

  /* ---------- DeepSeek ---------- */
  { name: 'deepseek-v3 (deepseek-chat)', vendor: 'DeepSeek', ctx: '64K', cats: ['chat', 'code'],
    official_in: 0.2, official_out: 0.8,
    tags: ['对话', '代码', '开源', '高性价比'],
    source: 'OpenRouter API 2026-06-20 (近 platform.deepseek.com 标准价)' },
  { name: 'deepseek-r1 (deepseek-reasoner)', vendor: 'DeepSeek', ctx: '64K', cats: ['reasoning'],
    official_in: 0.7, official_out: 2.5,
    tags: ['推理', '开源', '高性价比'],
    source: 'OpenRouter API 2026-06-20' },

  /* ---------- Mistral ---------- */
  { name: 'mistral-large', vendor: 'Mistral', ctx: '128K', cats: ['chat', 'code'],
    official_in: 2.0, official_out: 6.0,
    tags: ['对话', '代码', '欧洲'],
    source: 'OpenRouter API 2026-06-20 (mistral-large-2407, 与 mistral.ai 官方价一致)' },

  /* ---------- Meta ---------- */
  { name: 'llama-3.3-70b', vendor: 'Meta', ctx: '128K', cats: ['chat', 'code'],
    official_in: 0.1, official_out: 0.32,
    tags: ['对话', '代码', '开源'],
    source: 'OpenRouter API 2026-06-20 (托管代表价, Meta 模型本身开源免授权费)' },

  /* ---------- 阿里 通义千问 ---------- */
  { name: 'qwen-max', vendor: '阿里通义', ctx: '32K', cats: ['chat'],
    official_in: 1.6, official_out: 6.4,
    tags: ['对话', '中文', '阿里云'],
    source: 'help.aliyun.com/dashscope 官方价 (约 ¥0.02/¥0.06 每千 tokens 折算, OR 实时目录已下线) 2026-06' },

  /* ---------- 智谱 GLM ---------- */
  { name: 'glm-4-plus', vendor: '智谱AI', ctx: '128K', cats: ['chat'],
    official_in: null, official_out: null,
    tags: ['对话', '中文'],
    source: '待核 (bigmodel.cn 官方以 ¥ 计价, 约 ¥50/百万tokens; OR 实时目录已下线, USD 折算待核)' },

  /* ---------- 月之暗面 Moonshot ---------- */
  { name: 'moonshot-v1-128k', vendor: '月之暗面', ctx: '128K', cats: ['chat'],
    official_in: null, official_out: null,
    tags: ['对话', '中文', '长上下文'],
    source: '待核 (platform.moonshot.cn 以 ¥ 计价, 约 ¥60/百万tokens; OR 实时目录已下线, USD 折算待核)' }
];
