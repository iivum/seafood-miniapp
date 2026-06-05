import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { Truck } from 'lucide-react';
import { ordersApi } from './api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useToast } from '@/components/ui/toaster';
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

export function OrderDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['order-detail', id],
    queryFn: () => ordersApi.detail(id),
    enabled: Boolean(id),
  });

  const shipMutation = useMutation({
    mutationFn: () => ordersApi.ship(id),
    onSuccess: () => {
      toast.success('订单已发货');
      void queryClient.invalidateQueries({ queryKey: ['order-detail', id] });
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (err: Error) => toast.error(err.message ?? '发货失败'),
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>加载失败</CardTitle>
          <CardDescription>无法加载订单详情</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="outline" onClick={() => navigate('/admin/orders')}>
            返回列表
          </Button>
        </CardContent>
      </Card>
    );
  }

  const { order, customer, items } = data;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Link to="/admin/orders" className="text-small text-app-muted hover:underline">
            ← 返回订单列表
          </Link>
          <h1 className="text-h1 font-semibold">订单详情</h1>
          <p className="font-mono text-small text-app-muted">{order.id}</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={STATUS_VARIANT[order.status]}>{STATUS_LABEL[order.status]}</Badge>
          {order.status === 'PAID' ? (
            <Button onClick={() => shipMutation.mutate()} disabled={shipMutation.isPending}>
              <Truck className="mr-1 h-4 w-4" /> {shipMutation.isPending ? '处理中…' : '发货'}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>用户信息</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-body">
            <div>
              <span className="text-app-muted">昵称:</span> {customer.nickname}
            </div>
            <div>
              <span className="text-app-muted">用户 ID:</span> <span className="font-mono">{customer.id}</span>
            </div>
            <div>
              <span className="text-app-muted">手机:</span> {customer.phone ?? '—'}
            </div>
            <div>
              <span className="text-app-muted">注册时间:</span> {formatDateTime(customer.createdAt)}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>订单信息</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-body">
            <div>
              <span className="text-app-muted">金额:</span> <span className="font-semibold">{formatPrice(order.totalAmount)}</span>
            </div>
            <div>
              <span className="text-app-muted">创建时间:</span> {formatDateTime(order.createdAt)}
            </div>
            <div>
              <span className="text-app-muted">更新时间:</span> {formatDateTime(order.updatedAt)}
            </div>
            {order.cancelReason ? (
              <div>
                <span className="text-app-muted">取消原因:</span> {order.cancelReason}
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>行项</CardTitle>
          <CardDescription>共 {items.length} 项</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>商品</TableHead>
                <TableHead>分类</TableHead>
                <TableHead className="text-right">单价</TableHead>
                <TableHead className="text-right">数量</TableHead>
                <TableHead className="text-right">小计</TableHead>
                <TableHead>状态</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((it) => (
                <TableRow key={it.productId}>
                  <TableCell className="font-medium">{it.productName}</TableCell>
                  <TableCell className="text-app-muted">{it.product?.category ?? '已下架'}</TableCell>
                  <TableCell className="text-right">{formatPrice(it.unitPrice)}</TableCell>
                  <TableCell className="text-right">{it.quantity}</TableCell>
                  <TableCell className="text-right">{formatPrice(Number(it.unitPrice) * it.quantity)}</TableCell>
                  <TableCell>
                    {it.product ? (
                      <Badge variant={it.product.status === 'ACTIVE' ? 'success' : 'secondary'}>
                        {it.product.status === 'ACTIVE' ? '在售' : it.product.status === 'OUT_OF_STOCK' ? '缺货' : '已下架'}
                      </Badge>
                    ) : (
                      <Badge variant="secondary">已下架</Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

export default OrderDetailPage;
