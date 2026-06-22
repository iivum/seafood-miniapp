import { useQuery } from '@tanstack/react-query';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { formatPrice } from '@/lib/utils';
import { Link } from 'react-router-dom';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { dashboardApi } from './api';
import { useToast } from '@/components/ui/toaster';

/* ---- OD ad-02 KPI StatCard ---- */
function StatCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: number | string;
  hint?: string;
}) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-2xl text-accent">{value}</CardTitle>
      </CardHeader>
      {hint ? (
        <CardContent>
          <p className="text-sm text-muted">{hint}</p>
        </CardContent>
      ) : null}
    </Card>
  );
}

/* ---- 2.20 7 天趋势折线(Recharts)---- */
function TrendChart({ data }: { data: { date: string; count: number }[] }) {
  if (data.length === 0) {
    return <p className="text-sm text-muted">暂无趋势数据</p>;
  }
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border, oklch(91% 0.008 40))" />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 11, fill: 'var(--muted, oklch(50% 0.015 40))' }}
            tickFormatter={(d: string) => d.slice(5)} // MM-DD
          />
          <YAxis
            tick={{ fontSize: 11, fill: 'var(--muted, oklch(50% 0.015 40))' }}
            allowDecimals={false}
          />
          <Tooltip
            contentStyle={{
              background: 'var(--surface, oklch(100% 0 0))',
              border: '1px solid var(--border, oklch(91% 0.008 40))',
              borderRadius: 8,
              fontSize: 12,
            }}
            labelFormatter={(d: string) => `日期: ${d}`}
            formatter={(v: number) => [`${v} 单`, '订单数']}
          />
          <Line
            type="monotone"
            dataKey="count"
            stroke="var(--accent, oklch(64% 0.16 38))"
            strokeWidth={2}
            dot={{ r: 3 }}
            activeDot={{ r: 5 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

/* ---- 2.21 近期订单流(最近 10 单)---- */
function RecentOrdersList({
  orders,
}: {
  orders: {
    id: string;
    status: string;
    totalAmount: string;
    items: { productName: string; quantity: number }[];
    createdAt: string;
  }[];
}) {
  if (orders.length === 0) {
    return <p className="text-sm text-muted">暂无近期订单</p>;
  }
  return (
    <ul className="divide-y divide-app-divider">
      {orders.map((o) => {
        const first = o.items[0];
        const summary = first
          ? first.productName + (o.items.length > 1 ? ` 等 ${o.items.length} 件` : '')
          : '订单详情不可用';
        return (
          <li key={o.id} className="flex items-center justify-between py-3">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-fg">{o.id}</span>
                <span className={`status-chip status-chip--${o.status.toLowerCase()}`}>
                  {o.status}
                </span>
              </div>
              <p className="truncate text-sm text-muted">{summary}</p>
            </div>
            <div className="text-right">
              <p className="font-medium text-fg">{formatPrice(o.totalAmount)}</p>
              <p className="text-sm text-muted">
                {new Date(o.createdAt).toLocaleString('zh-CN', {
                  month: '2-digit',
                  day: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

/* ---- 2.21 库存预警(stock < 10,Top 10)---- */
function LowStockList({
  items,
}: {
  items: { id: string; name: string; stock: number; category: string }[];
}) {
  if (items.length === 0) {
    return <p className="text-sm text-muted">所有商品库存充足</p>;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>商品</TableHead>
          <TableHead>分类</TableHead>
          <TableHead className="text-right">剩余库存</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((p) => (
          <TableRow key={p.id}>
            <TableCell className="font-medium">{p.name}</TableCell>
            <TableCell className="text-muted">{p.category}</TableCell>
            <TableCell className="text-right">
              <span
                className={
                  p.stock === 0
                    ? 'font-semibold text-error'
                    : p.stock < 5
                      ? 'font-semibold text-warning'
                      : 'font-medium'
                }
              >
                {p.stock}
              </span>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-28" />
        ))}
      </div>
      <Skeleton className="h-64" />
    </div>
  );
}

export function DashboardPage() {
  const { data, isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardApi.get,
  });
  const toast = useToast();

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  if (isError || !data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>无法加载仪表盘</CardTitle>
          <CardDescription>请检查网络后重试</CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={() => void refetch()} disabled={isRefetching}>
            {isRefetching ? '重试中…' : '重试'}
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">仪表盘</h1>
          <p className="text-sm text-muted">订单、商品、销量概览</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => toast.show('导出报表功能开发中')}
        >
          导出报表
        </Button>
      </div>

      {/* OD ad-02: TODAY·GMV / ORDERS / AVG ORDER / CONVERSION */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="GMV 今日" value={formatPrice(data.orderStats.gmvToday)} hint="UTC+8 当日销售额" />
        <StatCard label="ORDERS 今日" value={data.orderStats.today} hint="UTC+8 当日 0 点至今" />
        <StatCard label="AVG ORDER 客单价" value={formatPrice(data.orderStats.avgOrderToday)} hint="今日 GMV / 今日订单数" />
        <StatCard label="CONVERSION 转化率" value="—" hint="需访客数据,功能开发中" />
      </div>

      {/* 2.20 7 天趋势折线 */}
      <Card>
        <CardHeader>
          <CardTitle>7 天订单趋势</CardTitle>
          <CardDescription>UTC+8 当日为分界,折线展示每日新增订单数</CardDescription>
        </CardHeader>
        <CardContent>
          <TrendChart data={data.trend7d} />
        </CardContent>
      </Card>

      {/* 2.21 近期订单流 */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle>近期订单</CardTitle>
            <CardDescription>最近 {data.recentOrders.length} 单,按时间倒序</CardDescription>
          </div>
          <Button asChild variant="outline" size="sm">
            <Link to="/orders">查看全部</Link>
          </Button>
        </CardHeader>
        <CardContent>
          <RecentOrdersList orders={data.recentOrders} />
        </CardContent>
      </Card>

      {/* 2.21 库存预警 + 分类分布 + 销量 Top 10 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>库存预警</CardTitle>
            <CardDescription>剩余库存 &lt; 10,按库存升序</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <LowStockList items={data.lowStock} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>分类分布</CardTitle>
            <CardDescription>各分类在售商品数</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {Object.entries(data.productStats.byCategory).map(([cat, count]) => (
                <span
                  key={cat}
                  className="inline-flex items-center gap-1 rounded-md bg-soft px-3 py-1 text-sm text-fg"
                >
                  <span className="font-medium">{cat}</span>
                  <span className="text-muted">{count}</span>
                </span>
              ))}
              {Object.keys(data.productStats.byCategory).length === 0 ? (
                <span className="text-sm text-muted">暂无数据</span>
              ) : null}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>销量 Top 10</CardTitle>
          <CardDescription>最近 500 单累计销量</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">#</TableHead>
                <TableHead>商品</TableHead>
                <TableHead>分类</TableHead>
                <TableHead className="text-right">价格</TableHead>
                <TableHead className="text-right">销量</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.topProducts.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted">
                    暂无数据
                  </TableCell>
                </TableRow>
              ) : (
                data.topProducts.map((row, idx) => (
                  <TableRow key={row.product?.id ?? idx}>
                    <TableCell className="text-muted">{idx + 1}</TableCell>
                    <TableCell className="font-medium">
                      {row.product?.name ?? '—'}
                    </TableCell>
                    <TableCell className="text-muted">
                      {row.product?.category ?? '—'}
                    </TableCell>
                    <TableCell className="text-right">
                      {row.product ? formatPrice(row.product.price) : '—'}
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {row.totalQuantitySold}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

export default DashboardPage;
