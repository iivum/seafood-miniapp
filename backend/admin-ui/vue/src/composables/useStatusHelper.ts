export function useStatusHelper() {
  const statusColors: Record<string, string> = {
    PENDING: '#f57f17',
    PAID: '#2e7d32',
    SHIPPED: '#1565c0',
    COMPLETED: '#0096c7',
    CANCELLED: '#c62828'
  }

  const statusLabels: Record<string, string> = {
    PENDING: '待支付',
    PAID: '已支付',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }

  function getStatusType(status: string): 'warning' | 'success' | 'info' | 'primary' | 'danger' {
    const map: Record<string, 'warning' | 'success' | 'info' | 'primary' | 'danger'> = {
      PENDING: 'warning',
      PAID: 'success',
      SHIPPED: 'info',
      COMPLETED: 'primary',
      CANCELLED: 'danger'
    }
    return map[status] ?? 'info'
  }

  return {
    statusColors,
    statusLabels,
    getStatusType
  }
}
