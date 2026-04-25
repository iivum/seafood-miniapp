export function useFormatters() {
  function formatPrice(value: number | undefined | null): string {
    if (value == null) return '¥0.00'
    return '¥' + value.toFixed(2)
  }

  function formatDate(date: string | null | undefined): string {
    if (!date) return '-'
    const d = new Date(date)
    return d.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  }

  return {
    formatPrice,
    formatDate
  }
}
