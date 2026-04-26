export function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  const normalized = value.replace('T', ' ').replace(/\.\d+$/, '')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}
