import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { ordersApi } from './api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { formatDateTime, formatPrice } from '@/lib/utils';
import type { OrderStatusCode } from '@/types/api';

const STATUS_VARIANT: Record<OrderStatusCode, 'warning' | 'info' | 'success' | 'secondary' | 'destructive'> = {
  PENDING: 'warning',
  PAID: 'info',
  SHIPPED: 'success',
  COMPLETED: 'secondary',
  CANCELLED: 'destructive',
};

const STATUS_LABEL: Record<OrderStatusCode, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
};

export function OrderListPage() {
  const [page, setPage] = useState(0);
  const pageSize = 20;
  const navigate = useNavigate();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['orders', page, pageSize],
    queryFn: () => ordersApi.list({ page, size: pageSize }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-h1 font-semibold">订单管理</h1>
        <p className="text-small text-app-muted">所有订单一览,点击行查看详情</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>订单列表</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-2 p-6">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-10" />
              ))}
            </div>
          ) : isError || !data ? (
            <div className="p-6 text-center text-app-muted">加载失败</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
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
                {data.content.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center text-app-muted">
                      暂无数据
                    </TableCell>
                  </TableRow>
                ) : (
                  data.content.map((o) => (
                    <TableRow
                      key={o.id}
                      className="cursor-pointer"
                      onClick={() => navigate(`/admin/orders/${o.id}`)}
                    >
                      <TableCell className="font-mono text-small">{o.id}</TableCell>
                      <TableCell className="text-app-muted">{o.userId}</TableCell>
                      <TableCell className="text-right">{formatPrice(o.totalAmount)}</TableCell>
                      <TableCell className="text-right">
                        {o.items.reduce((sum, it) => sum + it.quantity, 0)}
                      </TableCell>
                      <TableCell>
                        <Badge variant={STATUS_VARIANT[o.status]}>{STATUS_LABEL[o.status]}</Badge>
                      </TableCell>
                      <TableCell className="text-app-muted">{formatDateTime(o.createdAt)}</TableCell>
                      <TableCell className="text-right">
                        <ChevronRight className="h-4 w-4 text-app-muted" />
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
      {data && data.totalPages > 1 ? (
        <div className="flex items-center justify-end gap-2 text-small text-app-muted">
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
