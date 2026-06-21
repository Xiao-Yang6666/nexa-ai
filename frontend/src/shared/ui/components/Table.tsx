import type { ReactNode, TableHTMLAttributes } from 'react';

export interface TableColumn<T> {
  /** 列头文案 */
  header: ReactNode;
  /** 单元格渲染器 */
  cell: (row: T, index: number) => ReactNode;
  /** 可选列宽 */
  width?: string;
}

export interface TableProps<T> extends Omit<TableHTMLAttributes<HTMLTableElement>, 'children'> {
  columns: TableColumn<T>[];
  rows: T[];
  /** 取行 key */
  rowKey: (row: T, index: number) => string | number;
  /** 空态文案 */
  emptyText?: ReactNode;
  /** 加载态（显示骨架行） */
  loading?: boolean;
}

/**
 * 数据表格。token 化样式来自 tokens.css 的 table / th / td / .empty / .skeleton。
 * 泛型化以保证列渲染类型安全（不臆造行结构）。
 *
 * @example
 * <Table columns={cols} rows={data} rowKey={(r) => r.id} />
 */
export function Table<T>({
  columns,
  rows,
  rowKey,
  emptyText = '暂无数据',
  loading,
  ...rest
}: TableProps<T>) {
  if (loading) {
    return (
      <table {...rest}>
        <thead>
          <tr>
            {columns.map((c, i) => (
              <th key={i} style={c.width ? { width: c.width } : undefined}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: 5 }).map((_, r) => (
            <tr key={r}>
              {columns.map((__, c) => (
                <td key={c}>
                  <div className="skeleton" style={{ height: 16 }} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    );
  }

  if (rows.length === 0) {
    return <div className="empty">{emptyText}</div>;
  }

  return (
    <table {...rest}>
      <thead>
        <tr>
          {columns.map((c, i) => (
            <th key={i} style={c.width ? { width: c.width } : undefined}>
              {c.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, ri) => (
          <tr key={rowKey(row, ri)}>
            {columns.map((c, ci) => (
              <td key={ci}>{c.cell(row, ri)}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
