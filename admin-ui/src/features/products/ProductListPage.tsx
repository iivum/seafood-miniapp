import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2 } from 'lucide-react';
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useToast } from '@/components/ui/toaster';
import { formatPrice, formatDateTime } from '@/lib/utils';
import type { ProductResponse } from '@/types/api';

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

export function ProductListPage() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<ProductResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<ProductResponse | null>(null);
  const pageSize = 20;
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['products', page, pageSize],
    queryFn: () => productsApi.list({ page, size: pageSize }),
  });

  const { data: stats } = useQuery({
    queryKey: ['products-stats'],
    queryFn: productsApi.stats,
  });

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

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h1 font-semibold">商品管理</h1>
          <p className="text-small text-app-muted">
            {stats ? `共 ${stats.total} 款,在售 ${stats.onSale},缺货 ${stats.outOfStock}` : '加载中…'}
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus className="mr-1 h-4 w-4" /> 新增商品
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>商品列表</CardTitle>
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
                  <TableHead>名称</TableHead>
                  <TableHead>分类</TableHead>
                  <TableHead className="text-right">价格</TableHead>
                  <TableHead className="text-right">库存</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>更新时间</TableHead>
                  <TableHead className="w-32 text-right">操作</TableHead>
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
                  data.content.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell className="font-medium">{p.name}</TableCell>
                      <TableCell className="text-app-muted">{p.category}</TableCell>
                      <TableCell className="text-right">{formatPrice(p.price)}</TableCell>
                      <TableCell className="text-right">{p.stock}</TableCell>
                      <TableCell>
                        <Badge variant={STATUS_VARIANT[p.status]}>{STATUS_LABEL[p.status]}</Badge>
                      </TableCell>
                      <TableCell className="text-app-muted">{formatDateTime(p.updatedAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" onClick={() => setEditing(p)} aria-label={`编辑 ${p.name}`}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => setDeleting(p)}
                            aria-label={`删除 ${p.name}`}
                            className="text-feedback-error hover:text-feedback-error"
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
