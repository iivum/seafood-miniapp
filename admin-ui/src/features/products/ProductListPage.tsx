import { useState, useMemo, useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2, Copy, Download, Power, PowerOff, CheckSquare, Square } from 'lucide-react';
import { productsApi } from './api';
import { ProductForm, type ProductFormValues } from './ProductForm';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useToast } from '@/components/ui/toaster';
import { formatPrice, formatDateTime } from '@/lib/utils';
import type { ProductResponse, ProductCategory, ProductStatus } from '@/types/api';
import { PRODUCT_CATEGORIES, PRODUCT_STATUSES } from '@/types/api';

const STATUS_VARIANT: Record<ProductResponse['status'], 'success' | 'warning' | 'secondary'> = {
  ACTIVE: 'success',
  OUT_OF_STOCK: 'warning',
  DISCONTINUED: 'secondary',
};

const STATUS_LABEL: Record<ProductResponse['status'], string> = {
  ACTIVE: '在售',
  OUT_OF_STOCK: '缺货',
  DISCONTINUED: '已下架',
};

/**
 * 路线图 3.3 ad-03 商品管理 — shadcn DataTable + 5 分类 tab + 3 状态过滤 +
 * 批量上架/下架 + 单行复制(3.1 duplicate)+ 导出 CSV(3.2)。
 *
 * <p>依赖 3.3 后端扩:`POST /api/admin/products/batch-status`(0.5d 已落地)。
 */
export function ProductListPage() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<ProductResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<ProductResponse | null>(null);
  // 3.3 新增 4 个状态
  const [categoryTab, setCategoryTab] = useState<ProductCategory | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<ProductStatus | 'ALL'>('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const pageSize = 20;
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['products', page, pageSize, categoryTab, statusFilter],
    queryFn: () => productsApi.list({
      page,
      size: pageSize,
      category: categoryTab === 'ALL' ? undefined : categoryTab,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
    }),
  });

  const { data: stats } = useQuery({
    queryKey: ['products-stats'],
    queryFn: productsApi.stats,
  });

  // 切换 tab / filter 时清空选中集(防跨页勾选状态泄漏)
  useEffect(() => {
    setSelectedIds(new Set());
  }, [categoryTab, statusFilter, page]);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['products'] });
    void queryClient.invalidateQueries({ queryKey: ['products-stats'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const createMutation = useMutation({
    mutationFn: (body: ProductFormValues) => productsApi.create(body),
    onSuccess: () => {
      toast.success('商品已创建');
      setCreating(false);
      invalidate();
    },
    onError: (err: Error) => toast.error(err.message ?? '创建失败'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ProductFormValues }) => productsApi.update(id, body),
    onSuccess: () => {
      toast.success('商品已更新');
      setEditing(null);
      invalidate();
    },
    onError: (err: Error) => toast.error(err.message ?? '更新失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => productsApi.delete(id),
    onSuccess: () => {
      toast.success('商品已删除');
      setDeleting(null);
      invalidate();
    },
    onError: (err: Error) => toast.error(err.message ?? '删除失败'),
  });

  // 3.3 批量状态变更
  const batchStatusMutation = useMutation({
    mutationFn: ({ ids, status }: { ids: string[]; status: ProductStatus }) =>
      productsApi.batchStatus({ ids, status }),
    onSuccess: (res) => {
      toast.success(
        `已变更 ${res.successCount} 个商品状态${res.failedCount > 0 ? `,${res.failedCount} 个失败` : ''}`,
      );
      setSelectedIds(new Set());
      invalidate();
    },
    onError: (err: Error) => toast.error(err.message ?? '批量变更失败'),
  });

  // 3.3 单行复制
  const duplicateMutation = useMutation({
    mutationFn: (id: string) => productsApi.duplicate(id),
    onSuccess: (created) => {
      toast.success(`已复制: ${created.name}`);
      invalidate();
    },
    onError: (err: Error) => toast.error(err.message ?? '复制失败'),
  });

  // 3.3 导出 CSV(走 3.2 后端 /export 端点)
  function handleExport() {
    window.open('/api/admin/products/export', '_blank');
  }

  // 选中行 toggle / 全选 toggle
  const allIds = useMemo(() => (data?.content ?? []).map((p) => p.id), [data]);
  const allSelected = allIds.length > 0 && allIds.every((id) => selectedIds.has(id));
  const someSelected = allIds.some((id) => selectedIds.has(id));

  function toggleRow(id: string) {
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
      setSelectedIds(new Set(allIds));
    }
  }

  function handleBatchStatus(status: ProductStatus) {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) {
      toast.error('请先勾选要变更的商品');
      return;
    }
    batchStatusMutation.mutate({ ids, status });
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">商品管理</h1>
          <p className="text-sm text-muted">
            {stats ? `共 ${stats.total} 款,在售 ${stats.onSale},缺货 ${stats.outOfStock}` : '加载中…'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleExport}>
            <Download className="mr-1 h-4 w-4" /> 导出 CSV
          </Button>
          <Button onClick={() => setCreating(true)}>
            <Plus className="mr-1 h-4 w-4" /> 新增商品
          </Button>
        </div>
      </div>

      {/* 3.3 5 分类 tab + 3 状态过滤 */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-4">
            <CardTitle>商品列表</CardTitle>
            <Tabs value={statusFilter} onValueChange={(v) => setStatusFilter(v as ProductStatus | 'ALL')}>
              <TabsList>
                <TabsTrigger value="ALL">全部</TabsTrigger>
                {PRODUCT_STATUSES.map((s) => (
                  <TabsTrigger key={s} value={s}>{STATUS_LABEL[s]}</TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
          </div>
        </CardHeader>
        <CardContent>
          {/* 5 分类 tab(全部 + 鱼类/虾蟹/贝类/软体/海藻) */}
          <Tabs value={categoryTab} onValueChange={(v) => setCategoryTab(v as ProductCategory | 'ALL')}>
            <TabsList>
              <TabsTrigger value="ALL">全部分类</TabsTrigger>
              {PRODUCT_CATEGORIES.map((c) => (
                <TabsTrigger key={c} value={c}>{c}</TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          {/* 批量操作栏(选中 > 0 时显示) */}
          {someSelected && (
            <div className="mt-3 flex items-center gap-2 rounded-md border border-border bg-soft p-2 text-sm">
              <span className="text-muted">已选 {selectedIds.size} 项</span>
              <Button
                size="sm"
                variant="outline"
                onClick={() => handleBatchStatus('ACTIVE')}
                disabled={batchStatusMutation.isPending}
              >
                <Power className="mr-1 h-3 w-3" /> 批量上架
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => handleBatchStatus('DISCONTINUED')}
                disabled={batchStatusMutation.isPending}
              >
                <PowerOff className="mr-1 h-3 w-3" /> 批量下架
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setSelectedIds(new Set())}
                disabled={batchStatusMutation.isPending}
              >
                取消选择
              </Button>
            </div>
          )}

          <div className="mt-3">
            {isLoading ? (
              <div className="space-y-2">
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
                      <button
                        type="button"
                        aria-label={allSelected ? '取消全选' : '全选当前页'}
                        onClick={toggleAll}
                        className="inline-flex h-4 w-4 items-center justify-center text-muted hover:text-fg"
                      >
                        {allSelected ? <CheckSquare className="h-4 w-4" /> : <Square className="h-4 w-4" />}
                      </button>
                    </TableHead>
                    <TableHead>名称</TableHead>
                    <TableHead>分类</TableHead>
                    <TableHead className="text-right">价格</TableHead>
                    <TableHead className="text-right">库存</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>更新时间</TableHead>
                    <TableHead className="w-48 text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center text-muted">
                        暂无数据
                      </TableCell>
                    </TableRow>
                  ) : (
                    data.content.map((p) => (
                      <TableRow key={p.id} data-state={selectedIds.has(p.id) ? 'selected' : undefined}>
                        <TableCell>
                          <Checkbox
                            checked={selectedIds.has(p.id)}
                            onCheckedChange={() => toggleRow(p.id)}
                            aria-label={`选择 ${p.name}`}
                          />
                        </TableCell>
                        <TableCell className="font-medium">{p.name}</TableCell>
                        <TableCell className="text-muted">{p.category}</TableCell>
                        <TableCell className="text-right">{formatPrice(p.price)}</TableCell>
                        <TableCell className="text-right">{p.stock}</TableCell>
                        <TableCell>
                          <Badge variant={STATUS_VARIANT[p.status]}>{STATUS_LABEL[p.status]}</Badge>
                        </TableCell>
                        <TableCell className="text-muted">{formatDateTime(p.updatedAt)}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            <Button variant="ghost" size="icon" onClick={() => duplicateMutation.mutate(p.id)} aria-label={`复制 ${p.name}`} title="复制">
                              <Copy className="h-4 w-4" />
                            </Button>
                            <Button variant="ghost" size="icon" onClick={() => setEditing(p)} aria-label={`编辑 ${p.name}`} title="编辑">
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => setDeleting(p)}
                              aria-label={`删除 ${p.name}`}
                              className="text-error hover:text-error"
                              title="删除"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            )}
          </div>
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

      <Dialog open={creating} onOpenChange={(open) => !open && setCreating(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新增商品</DialogTitle>
            <DialogDescription>填写商品信息后点击保存</DialogDescription>
          </DialogHeader>
          <ProductForm
            submitLabel="创建"
            submitting={createMutation.isPending}
            onCancel={() => setCreating(false)}
            onSubmit={async (values) => {
              await createMutation.mutateAsync(values);
            }}
          />
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(editing)} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑商品</DialogTitle>
            <DialogDescription>{editing?.name}</DialogDescription>
          </DialogHeader>
          {editing ? (
            <ProductForm
              submitLabel="保存"
              submitting={updateMutation.isPending}
              onCancel={() => setEditing(null)}
              defaultValues={{
                name: editing.name,
                description: editing.description,
                price: Number(editing.price),
                stock: editing.stock,
                category: editing.category,
                status: editing.status,
                imageUrl: editing.imageUrl,
              }}
              onSubmit={async (values) => {
                await updateMutation.mutateAsync({ id: editing.id, body: values });
              }}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(deleting)} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除商品</DialogTitle>
            <DialogDescription>确认要删除「{deleting?.name}」?此操作不可撤销。</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setDeleting(null)} disabled={deleteMutation.isPending}>
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={() => deleting && deleteMutation.mutate(deleting.id)}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? '删除中…' : '确认删除'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default ProductListPage;
