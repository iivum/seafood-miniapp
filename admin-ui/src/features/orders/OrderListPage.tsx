import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, Download, Loader2, Package, Truck } from 'lucide-react';
import { ordersApi } from './api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Checkbox } from '@/components/ui/checkbox';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { formatDateTime, formatPrice } from '@/lib/utils';
import type { OrderStatusCode } from '@/types/api';
import { useToast } from '@/components/ui/toaster';

const STATUS_VARIANT: Record<OrderStatusCode, 'warning' | 'info' | 'success' | 'secondary' | 'destructive' | 'outline'> = {
  PENDING: 'warning',
  PAID: 'info',
  SHIPPED: 'success',
  COMPLETED: 'secondary',
  CANCELLED: 'destructive',
  REFUNDING: 'outline',
  REFUNDED: 'secondary',
};

const STATUS_LABEL: Record<OrderStatusCode, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
};

/** 4.16:状态 tabs。后端 list 不支持 status 过滤,前端在 client 端按当前 tab 过滤。 */
type StatusTab = 'ALL' | OrderStatusCode;
const STATUS_TABS: { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
];

export function OrderListPage() {
  const [page, setPage] = useState(0);
  const [statusTab, setStatusTab] = useState<StatusTab>('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const pageSize = 20;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['orders', page, pageSize],
    queryFn: () => ordersApi.list({ page, size: pageSize }),
  });

  // 客户端按 status tab 过滤。后端不分页+过滤,client 端过滤对 20 单/页足够。
  const filteredContent = useMemo(() => {
    if (!data?.content) return [];
    if (statusTab === 'ALL') return data.content;
    return data.content.filter((o) => o.status === statusTab);
  }, [data?.content, statusTab]);

  // 当前页所有可选行(过滤后)
  const selectableIds = useMemo(
    () => filteredContent.filter((o) => o.status === 'PAID').map((o) => o.id), // 只允许勾 PAID(批量发货前置条件)
    [filteredContent]
  );
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.has(id));
  const someSelected = selectableIds.some((id) => selectedIds.has(id)) && !allSelected;

  // 切 tab / 翻页时清空选择(避免选中消失的订单)
  function clearSelection() {
    if (selectedIds.size > 0) setSelectedIds(new Set());
  }

  // 4.13:批量发货
  const batchShip = useMutation({
    mutationFn: (orderIds: string[]) => ordersApi.batchShip({ orderIds }),
    onSuccess: (res) => {
      if (res.successCount > 0) {
        toast.success(`已发货 ${res.successCount} 单`);
      }
      if (res.failedCount > 0) {
        const first = res.failed[0]?.reason ?? '未知';
        toast.warning(`${res.failedCount} 单失败:${first}${res.failed.length > 1 ? '...' : ''}`);
      }
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      clearSelection();
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : '批量发货失败';
      toast.error(msg);
    },
  });

  // 4.15:导出 CSV(浏览器直接下载,Content-Disposition 由后端带)
  const exportCsv = useMutation({
    mutationFn: () => ordersApi.exportCsv(),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `orders-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success('CSV 已开始下载');
    },
    onError: () => toast.error('导出失败'),
  });

  function toggleOne(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAll() {
    if (allSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(selectableIds));
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">订单管理</h1>
          <p className="text-sm text-muted">所有订单一览,点击行查看详情</p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => exportCsv.mutate()}
            disabled={exportCsv.isPending}
          >
            {exportCsv.isPending ? (
              <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
            ) : (
              <Download className="mr-1.5 h-4 w-4" />
            )}
            导出 CSV
          </Button>
        </div>
      </div>

      {/* 状态 tabs */}
      <Tabs
        value={statusTab}
        onValueChange={(v: string) => {
          setStatusTab(v as StatusTab);
          setPage(0);
          clearSelection();
        }}
      >
        <TabsList>
          {STATUS_TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>订单列表</CardTitle>
            {selectedIds.size > 0 ? (
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted">已选 {selectedIds.size} 单</span>
                <Button
                  size="sm"
                  onClick={() => batchShip.mutate([...selectedIds])}
                  disabled={batchShip.isPending}
                >
                  {batchShip.isPending ? (
                    <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
                  ) : (
                    <Truck className="mr-1.5 h-4 w-4" />
                  )}
                  {batchShip.isPending ? '发货中' : '批量发货'}
                </Button>
              </div>
            ) : null}
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-2 p-6">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-10" />
              ))}
            </div>
          ) : isError || !data ? (
            <div className="p-6 text-center text-muted">加载失败</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">
                    <Checkbox
                      checked={allSelected}
                      ref={(el: HTMLInputElement | null) => {
                        if (el) el.indeterminate = someSelected;
                      }}
                      onCheckedChange={toggleAll}
                      disabled={selectableIds.length === 0}
                      aria-label="全选本页已付款订单"
                    />
                  </TableHead>
                  <TableHead>订单号</TableHead>
                  <TableHead>用户</TableHead>
                  <TableHead className="text-right">金额</TableHead>
                  <TableHead className="text-right">数量</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>创建时间</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredContent.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8}>
                      <div className="flex flex-col items-center gap-2 py-12 text-muted">
                        <Package className="h-10 w-10 opacity-40" />
                        <p className="font-medium">暂无订单</p>
                        <p className="text-xs">当前筛选条件下没有订单</p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredContent.map((o) => {
                    const selectable = o.status === 'PAID';
                    const checked = selectedIds.has(o.id);
                    return (
                      <TableRow
                        key={o.id}
                        className="cursor-pointer"
                        onClick={() => navigate(`/admin/orders/${o.id}`)}
                      >
                        <TableCell onClick={(e) => e.stopPropagation()}>
                          <Checkbox
                            checked={checked}
                            onCheckedChange={() => toggleOne(o.id)}
                            disabled={!selectable}
                            aria-label={`选择订单 ${o.id}`}
                          />
                        </TableCell>
                        <TableCell className="font-mono text-sm">{o.id}</TableCell>
                        <TableCell className="text-muted">{o.userId}</TableCell>
                        <TableCell className="text-right">{formatPrice(o.totalAmount)}</TableCell>
                        <TableCell className="text-right">
                          {o.items.reduce((sum, it) => sum + it.quantity, 0)}
                        </TableCell>
                        <TableCell>
                          <Badge variant={STATUS_VARIANT[o.status]}>{STATUS_LABEL[o.status]}</Badge>
                        </TableCell>
                        <TableCell className="text-muted">{formatDateTime(o.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <ChevronRight className="h-4 w-4 text-muted" />
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
      {data && data.totalPages > 1 ? (
        <div className="flex items-center justify-end gap-2 text-sm text-muted">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            上一页
          </Button>
          <span>
            第 {page + 1} / {data.totalPages} 页
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      ) : null}
    </div>
  );
}

export default OrderListPage;
