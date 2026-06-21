import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { bannersApi } from './api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useToast } from '@/components/ui/toaster';
import type { BannerRequest, BannerResponse, BannerTone } from '@/types/api';
import { BANNER_TONES } from '@/types/api';

const EMPTY_FORM: BannerRequest = {
  tone: 'ACCENT',
  emoji: '',
  title: '',
  subtitle: '',
  targetProductId: null,
  sortOrder: 0,
  active: true,
};

function toForm(b: BannerResponse): BannerRequest {
  return {
    tone: b.tone,
    emoji: b.emoji,
    title: b.title,
    subtitle: b.subtitle,
    targetProductId: b.targetProductId,
    sortOrder: b.sortOrder,
    active: b.status === 'ACTIVE',
  };
}

export default function BannerListPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState<BannerResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<BannerRequest>(EMPTY_FORM);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['banners'],
    queryFn: bannersApi.listAll,
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['banners'] });
  };

  const closeDialog = () => {
    setCreating(false);
    setEditing(null);
    setForm(EMPTY_FORM);
  };

  const createMutation = useMutation({
    mutationFn: (body: BannerRequest) => bannersApi.create(body),
    onSuccess: () => {
      toast.success('Banner 已创建');
      invalidate();
      closeDialog();
    },
    onError: () => toast.error('创建失败'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: BannerRequest }) => bannersApi.update(id, body),
    onSuccess: () => {
      toast.success('Banner 已更新');
      invalidate();
      closeDialog();
    },
    onError: () => toast.error('更新失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => bannersApi.delete(id),
    onSuccess: () => {
      toast.success('Banner 已删除');
      invalidate();
    },
    onError: () => toast.error('删除失败'),
  });

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setCreating(true);
  };

  const openEdit = (b: BannerResponse) => {
    setForm(toForm(b));
    setEditing(b);
  };

  const submit = () => {
    const body: BannerRequest = {
      ...form,
      targetProductId: form.targetProductId?.trim() ? form.targetProductId.trim() : null,
      sortOrder: Number(form.sortOrder) || 0,
    };
    if (editing) {
      updateMutation.mutate({ id: editing.id, body });
    } else {
      createMutation.mutate(body);
    }
  };

  const dialogOpen = creating || editing !== null;
  const saving = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Banner 管理</h1>
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          新建 Banner
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>首页轮播 Banner</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : isError ? (
            <p className="text-sm text-destructive">加载 banner 失败</p>
          ) : !data || data.length === 0 ? (
            <p className="text-sm text-muted-foreground">暂无 banner</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>排序</TableHead>
                  <TableHead>标题</TableHead>
                  <TableHead>副标题</TableHead>
                  <TableHead>色调</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((b) => (
                  <TableRow key={b.id}>
                    <TableCell>{b.sortOrder}</TableCell>
                    <TableCell>
                      <span className="mr-1">{b.emoji}</span>
                      {b.title}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{b.subtitle}</TableCell>
                    <TableCell>{b.tone}</TableCell>
                    <TableCell>
                      <Badge variant={b.status === 'ACTIVE' ? 'default' : 'secondary'}>
                        {b.status === 'ACTIVE' ? '启用' : '停用'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="sm" aria-label="编辑" onClick={() => openEdit(b)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label="删除"
                        onClick={() => deleteMutation.mutate(b.id)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={(open) => !open && closeDialog()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? '编辑 Banner' : '新建 Banner'}</DialogTitle>
            <DialogDescription>首页 hero 轮播。空 targetProductId = 纯展示,不跳转。</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="banner-tone">色调</Label>
                <Select value={form.tone} onValueChange={(v) => setForm({ ...form, tone: v as BannerTone })}>
                  <SelectTrigger id="banner-tone">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {BANNER_TONES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="banner-emoji">Emoji</Label>
                <Input
                  id="banner-emoji"
                  value={form.emoji}
                  onChange={(e) => setForm({ ...form, emoji: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="banner-title">标题</Label>
              <Input
                id="banner-title"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="banner-subtitle">副标题</Label>
              <Input
                id="banner-subtitle"
                value={form.subtitle}
                onChange={(e) => setForm({ ...form, subtitle: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="banner-target">目标商品 ID(可空)</Label>
                <Input
                  id="banner-target"
                  value={form.targetProductId ?? ''}
                  onChange={(e) => setForm({ ...form, targetProductId: e.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="banner-sort">排序</Label>
                <Input
                  id="banner-sort"
                  type="number"
                  value={form.sortOrder}
                  onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })}
                />
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="banner-active">状态</Label>
              <Select
                value={form.active ? 'ACTIVE' : 'INACTIVE'}
                onValueChange={(v) => setForm({ ...form, active: v === 'ACTIVE' })}
              >
                <SelectTrigger id="banner-active">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ACTIVE">启用</SelectItem>
                  <SelectItem value="INACTIVE">停用</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeDialog}>
              取消
            </Button>
            <Button onClick={submit} disabled={saving || !form.title.trim() || !form.subtitle.trim() || !form.emoji.trim()}>
              {saving ? '保存中…' : '保存'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
