/**
 * 轻量语法高亮器（零依赖，输出原型同款 token 类）。
 *
 * 设计系统冻结语法高亮为「token tint」配色（docs-shell.css 的 .t-* 类），
 * 此处不引第三方库（避免裸色值/体积），用正则把代码切成带 class 的片段。
 * 覆盖文档站实际用到的 5 种语言：curl/bash、python、node(js/ts)、go、json。
 *
 * 输出为 token 片段数组，由 CodeBlock 渲染成 <span class="t-xx">。
 * 这是「够用」的高亮（关键字/字符串/数字/注释/函数名/占位变量），
 * 不追求 AST 级精确——文档代码块是展示性质，可读即可。
 */

/** 高亮后的片段：text + 可选 token 类（无类即普通文本）。 */
export interface Token {
  text: string;
  cls?: string;
}

const PLACEHOLDER = /\$[A-Z_][A-Z0-9_]*|sk-nexa-\.{3}|sk-nex\.{3}xxxx/;

/**
 * 把一行代码切成 token。按优先级：注释 > 字符串 > 占位变量 > 数字 > 关键字 > 函数名。
 * 简化实现：先用一个总正则带捕获组扫描，未命中部分作为普通文本。
 */
function highlightLine(line: string, lang: string): Token[] {
  const tokens: Token[] = [];
  // 各语言关键字集合
  const KEYWORDS: Record<string, RegExp> = {
    python: /\b(from|import|def|class|return|if|else|elif|for|while|with|as|True|False|None|in|not|and|or)\b/,
    node: /\b(import|from|const|let|var|function|return|await|async|new|if|else|for|while|export|default|true|false|null|undefined)\b/,
    go: /\b(package|import|func|return|if|else|for|range|var|const|type|struct|map|chan|go|defer|nil|true|false)\b/,
    curl: /\b(curl|export)\b/,
    bash: /\b(curl|export|echo)\b/,
    json: /\b(true|false|null)\b/,
    text: /(?!x)x/, // never
  };
  const kw = KEYWORDS[lang] ?? KEYWORDS.text;

  let rest = line;
  // 综合扫描：注释 / 双引号串 / 单引号串 / 占位 / 数字 / 关键字 / 函数调用名
  const master = new RegExp(
    [
      '(#[^\\n]*|//[^\\n]*)', // 1 注释
      '("(?:[^"\\\\]|\\\\.)*"|\'(?:[^\'\\\\]|\\\\.)*\')', // 2 字符串
      `(${PLACEHOLDER.source})`, // 3 占位变量
      '(\\b\\d+(?:\\.\\d+)?\\b)', // 4 数字
      `(${kw.source})`, // 5 关键字
      '([A-Za-z_][A-Za-z0-9_]*)(?=\\s*\\()', // 6 函数名（后跟左括号）
    ].join('|'),
    'g',
  );

  let lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = master.exec(rest)) !== null) {
    if (m.index > lastIndex) {
      tokens.push({ text: rest.slice(lastIndex, m.index), cls: 't-pun' });
    }
    if (m[1]) tokens.push({ text: m[1], cls: 't-com' });
    else if (m[2]) tokens.push({ text: m[2], cls: 't-str' });
    else if (m[3]) tokens.push({ text: m[3], cls: 't-var' });
    else if (m[4]) tokens.push({ text: m[4], cls: 't-num' });
    else if (m[5]) tokens.push({ text: m[5], cls: 't-key' });
    else if (m[6]) tokens.push({ text: m[6], cls: 't-fn' });
    lastIndex = master.lastIndex;
    if (m[0] === '') master.lastIndex++; // 防空匹配死循环
  }
  if (lastIndex < rest.length) {
    tokens.push({ text: rest.slice(lastIndex), cls: 't-pun' });
  }
  return tokens;
}

/** 把整段代码高亮成「行 -> token 数组」结构。保留换行供渲染。 */
export function highlight(code: string, lang: string): Token[][] {
  return code.split('\n').map((line) => highlightLine(line, lang));
}
