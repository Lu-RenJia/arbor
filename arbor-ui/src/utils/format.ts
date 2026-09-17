/**
 * 将 ISO 日期字符串格式化为 yyyy-MM-dd HH:mm:ss
 * 例如: "2026-07-14T17:41:35" → "2026-07-14 17:41:35"
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  // 替换 T 分隔符为空格，截掉毫秒部分
  return value.replace('T', ' ').substring(0, 19) || value
}

/**
 * 将 ISO 日期字符串格式化为 yyyy-MM-dd
 * 例如: "2026-07-14T17:41:35" → "2026-07-14"
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  return value.substring(0, 10)
}

/**
 * 格式化耗时（毫秒），大于1000ms显示秒
 * 例如: 1500 → "1.50s", 450 → "450ms"
 */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms >= 1000) return (ms / 1000).toFixed(2) + 's'
  return ms + 'ms'
}
