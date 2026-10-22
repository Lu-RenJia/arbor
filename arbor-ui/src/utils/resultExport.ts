/**
 * 流程结果 → 可粘贴文本（TSV / 单列）
 *
 * 粘贴目标是 Excel / 数据库客户端的结果网格：两者剪贴板粘贴都按 Tab 分列、换行分行，
 * 所以统一输出无引号的 TSV。值内嵌的 Tab / 换行会破坏行列结构，替换为空格。
 */

export interface NormalizedResult {
  /** 是否为对象形态的表格数据（对象数组 / 单对象） */
  tabular: boolean
  /** 表格列名（原始字段名，保持 JSON 中的顺序） */
  columns: string[]
  /** 表格行；标量数组时为单元素行 */
  rows: any[][]
  /** 非表格数据（字符串 / 数字 / 布尔）的原样文本；null 表示无数据 */
  rawText: string | null
}

/** 是否为普通对象（数组不算） */
function isPlainObject(value: any): boolean {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/**
 * 把结果数据归一化成表格 / 单列 / 原始文本三种形态
 *
 * - `[{"id":1}]`   → 表格，列为对象 key 的并集
 * - `["a","b"]`    → 单列，每行一个值
 * - `{"id":1}`     → 表格，一行
 * - `"abc"`        → 原始文本
 */
export function normalizeResult(data: any): NormalizedResult {
  if (data === null || data === undefined) {
    return { tabular: false, columns: [], rows: [], rawText: null }
  }

  if (Array.isArray(data)) {
    if (data.length === 0) {
      return { tabular: false, columns: [], rows: [], rawText: null }
    }
    // 全部是标量：单列，一行一个值
    if (!data.some(isPlainObject)) {
      return { tabular: false, columns: [], rows: data.map(v => [v]), rawText: null }
    }
    // 对象数组（混合标量时放进 value 列）：列取所有行的并集，避免首行缺字段导致后续列丢失
    const columns: string[] = []
    const seen = new Set<string>()
    let hasScalar = false
    for (const item of data) {
      if (isPlainObject(item)) {
        for (const key of Object.keys(item)) {
          if (!seen.has(key)) { seen.add(key); columns.push(key) }
        }
      } else {
        hasScalar = true
      }
    }
    if (hasScalar) columns.push('value')
    const rows = data.map(item => {
      if (isPlainObject(item)) return columns.map(key => item[key])
      return columns.map(key => (key === 'value' ? item : undefined))
    })
    return { tabular: true, columns, rows, rawText: null }
  }

  if (typeof data === 'string') {
    return { tabular: false, columns: [], rows: [], rawText: data }
  }

  if (isPlainObject(data)) {
    const columns = Object.keys(data)
    return { tabular: true, columns, rows: [columns.map(key => data[key])], rawText: null }
  }

  // number / boolean 等标量
  return { tabular: false, columns: [], rows: [], rawText: String(data) }
}

/** 单元格转文本：不加引号；内嵌 Tab / 换行替换为空格，避免粘贴后错列错行 */
function cellText(value: any): string {
  if (value === null || value === undefined) return ''
  let text: string
  if (typeof value === 'string') {
    text = value
  } else if (typeof value === 'number' || typeof value === 'boolean') {
    text = String(value)
  } else {
    // 嵌套对象 / 数组无法拆列，保留紧凑 JSON
    try {
      text = JSON.stringify(value)
    } catch {
      text = String(value)
    }
  }
  return text.replace(/[\t\r\n]+/g, ' ')
}

/**
 * 生成可粘贴文本
 *
 * @param data   流程结果数据（result.data）
 * @param header 对象表格是否输出表头；标量数组没有表头
 */
export function toPasteText(data: any, header = true): string {
  const norm = normalizeResult(data)
  if (norm.rawText !== null) return norm.rawText

  // 单列（标量数组）：一行一个值
  if (norm.columns.length === 0) {
    return norm.rows.map(row => cellText(row[0])).join('\n')
  }

  const lines: string[] = []
  if (header) {
    lines.push(norm.columns.map(column => column.replace(/[\t\r\n]+/g, ' ')).join('\t'))
  }
  for (const row of norm.rows) {
    lines.push(row.map(cellText).join('\t'))
  }
  return lines.join('\n')
}

/** 显式列定义：key 取值字段，label 表头文字（缺省用 key） */
export interface PasteColumn {
  key: string
  label?: string
}

/**
 * 按显式列定义生成 TSV（用于概览、映射等已经加工过的数据）
 *
 * @param columns 列定义（顺序即输出顺序）
 * @param rows    行数据
 * @param header  是否输出表头
 */
export function rowsToPasteText(columns: PasteColumn[], rows: any[], header = true): string {
  const lines: string[] = []
  if (header) {
    lines.push(columns.map(column => (column.label ?? column.key).replace(/[\t\r\n]+/g, ' ')).join('\t'))
  }
  for (const row of rows) {
    lines.push(columns.map(column => cellText(row?.[column.key])).join('\t'))
  }
  return lines.join('\n')
}

/** 文本统计，用于复制前提示行数与字符数 */
export function textStats(text: string): { lines: number; chars: number } {
  if (!text) return { lines: 0, chars: 0 }
  return { lines: text.split('\n').length, chars: text.length }
}
