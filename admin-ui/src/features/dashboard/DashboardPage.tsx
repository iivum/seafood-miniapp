import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { formatPrice } from '@/lib/utils';
import { dashboardApi } from './api';

function StatCard({ label, value, hint }: { label: string; value: number | string; hint?: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{label}</CardDescription>
        <CardTitle className="text-h1 text-primary-500">{value}</CardTitle>
      </CardHeader>
      {hint ? (
        <CardContent>
          <p className="text-small text-app-muted">{hint}</p>
        </CardContent>
      ) : null}
    </Card>
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
      <div>
        <h1 className="text-h1 font-semibold">仪表盘</h1>
        <p className="text-small text-app-muted">订单、商品、销量概览</p>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="今日订单" value={data.orderStats.today} hint="UTC+8 当日 0 点至今" />
        <StatCard label="本周订单" value={data.orderStats.week} hint="本周一 0 点至今" />
        <StatCard label="本月订单" value={data.orderStats.month} hint="本月 1 号 0 点至今" />
        <StatCard
          label="在售商品"
          value={data.productStats.onSale}
          hint={`共 ${data.productStats.total} 款 · 缺货 ${data.productStats.outOfStock}`}
        />
      </div>
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
                className="inline-flex items-center gap-1 rounded-md bg-app-divider px-3 py-1 text-small text-app-text"
              >
                <span className="font-medium">{cat}</span>
                <span className="text-app-muted">{count}</span>
              </span>
            ))}
            {Object.keys(data.productStats.byCategory).length === 0 ? (
              <span className="text-small text-app-muted">暂无数据</span>
            ) : null}
          </div>
        </CardContent>
      </Card>
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
                  <TableCell colSpan={5} className="text-center text-app-muted">
                    暂无数据
                  </TableCell>
                </TableRow>
              ) : (
                data.topProducts.map((row, idx) => (
                  <TableRow key={row.product?.id ?? idx}>
                    <TableCell className="text-app-muted">{idx + 1}</TableCell>
                    <TableCell className="font-medium">{row.product?.name ?? '—'}</TableCell>
                    <TableCell className="text-app-muted">{row.product?.category ?? '—'}</TableCell>
                    <TableCell className="text-right">{row.product ? formatPrice(row.product.price) : '—'}</TableCell>
                    <TableCell className="text-right font-medium">{row.totalQuantitySold}</TableCell>
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
