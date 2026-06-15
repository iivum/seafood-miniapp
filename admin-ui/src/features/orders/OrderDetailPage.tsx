import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { Truck, Printer, X, Undo2 } from 'lucide-react';
import { ordersApi } from './api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useToast } from '@/components/ui/toaster';
import { formatDateTime, formatPrice } from '@/lib/utils';
import type { OrderStatusCode } from '@/types/api';
import { OrderTrackingTimeline } from './OrderTrackingTimeline';

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

/**
 * 路线图 4.18 — ad-06 订单详情 3 列布局。
 *
 * <p>3 列结构:
 * <ul>
 *   <li>左(订单商品 + 金额明细):行项表格 + 总价/件数/下单/更新时间</li>
 *   <li>中(用户信息 + 收货地址):昵称 / ID / 手机 / 注册时间 + 收货地址(本迭代空,
 *       等后续地址模块接入 — 详情页有占位)</li>
 *   <li>右(物流时间线 + 操作按钮):4.4 OrderTrackingTimeline + 发货/打印拣货单/取消/查看退款
 *       操作按钮组(按钮按 Order.status 显隐)</li>
 * </ul>
 * <p>布局:`grid-cols-1 lg:grid-cols-3`,移动端纵向堆叠(1 列),桌面 3 列。
 */
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
      {/* header */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Link to="/admin/orders" className="text-sm text-muted hover:underline">
            ← 返回订单列表
          </Link>
          <h1 className="text-2xl font-semibold">订单详情</h1>
          <p className="font-mono text-sm text-muted">{order.id}</p>
        </div>
        <Badge variant={STATUS_VARIANT[order.status]}>{STATUS_LABEL[order.status]}</Badge>
      </div>

      {/* 3 列布局 — 桌面 lg:grid-cols-3,移动端堆叠 */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* 左:订单商品 + 金额明细 */}
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>订单商品</CardTitle>
              <CardDescription>共 {items.length} 项</CardDescription>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>商品</TableHead>
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
                      <TableCell className="text-right">{formatPrice(it.unitPrice)}</TableCell>
                      <TableCell className="text-right">{it.quantity}</TableCell>
                      <TableCell className="text-right">
                        {formatPrice(Number(it.unitPrice) * it.quantity)}
                      </TableCell>
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
          <Card>
            <CardHeader>
              <CardTitle>金额明细</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1 text-sm">
              <div className="flex justify-between">
                <span className="text-muted">商品总额</span>
                <span>{formatPrice(order.totalAmount)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">配送费</span>
                <span>—</span>
              </div>
              <div className="flex justify-between border-t border-border pt-1 font-semibold">
                <span>实付</span>
                <span>{formatPrice(order.totalAmount)}</span>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* 中:用户信息 + 收货地址 */}
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>用户信息</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1 text-sm">
              <div>
                <span className="text-muted">昵称:</span> {customer.nickname}
              </div>
              <div>
                <span className="text-muted">用户 ID:</span> <span className="font-mono">{customer.id}</span>
              </div>
              <div>
                <span className="text-muted">手机:</span> {customer.phone ?? '—'}
              </div>
              <div>
                <span className="text-muted">注册时间:</span> {formatDateTime(customer.createdAt)}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>收货地址</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted">
              {/* 路线图待办:地址模块(Sprint 3+)。本迭代在用户主键上有 addresses 数组
                  (UserResponse.addresses),admin 端先放占位 — 等地址模块接入后这里展示
                  实际收货人/手机/省市区/详细地址。 */}
              地址模块未接入,详情暂不可用
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>订单时间</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1 text-sm">
              <div>
                <span className="text-muted">创建:</span> {formatDateTime(order.createdAt)}
              </div>
              <div>
                <span className="text-muted">更新:</span> {formatDateTime(order.updatedAt)}
              </div>
              {order.cancelReason ? (
                <div>
                  <span className="text-muted">取消原因:</span> {order.cancelReason}
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        {/* 右:物流时间线 + 操作按钮 */}
        <div className="space-y-4">
          <OrderTrackingTimeline order={order} />
          <Card>
            <CardHeader>
              <CardTitle>操作</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {order.status === 'PAID' ? (
                <Button
                  className="w-full"
                  onClick={() => shipMutation.mutate()}
                  disabled={shipMutation.isPending}
                >
                  <Truck className="mr-2 h-4 w-4" />
                  {shipMutation.isPending ? '处理中…' : '发货'}
                </Button>
              ) : null}
              {/* 打印拣货单 — 任意状态(管理员手头需要就打印,本迭代不限制) */}
              <Button
                variant="outline"
                className="w-full"
                onClick={() => {
                  // 后端 4.14 GET /admin/orders/{id}/print-picklist 返回可打印 HTML
                  // 在新窗口打开,admin 端 Ctrl+P 另存 PDF
                  window.open(`/api/admin/orders/${order.id}/print-picklist`, '_blank');
                }}
              >
                <Printer className="mr-2 h-4 w-4" />
                打印拣货单
              </Button>
              {/* 取消订单(仅 PENDING / PAID 允许) */}
              {order.status === 'PENDING' || order.status === 'PAID' ? (
                <Button variant="destructive" className="w-full" disabled>
                  <X className="mr-2 h-4 w-4" />
                  取消订单(未实现)
                </Button>
              ) : null}
              {/* 查看退款 — 跳审核页 + 滚到该订单(后续 4.18 增强可挂 query param) */}
              {order.status === 'REFUNDING' || order.status === 'REFUNDED' ? (
                <Button
                  variant="outline"
                  className="w-full"
                  onClick={() => navigate('/admin/refunds')}
                >
                  <Undo2 className="mr-2 h-4 w-4" />
                  查看退款
                </Button>
              ) : null}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

export default OrderDetailPage;
