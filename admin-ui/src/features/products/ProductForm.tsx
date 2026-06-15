import { useEffect, useState } from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { PRODUCT_CATEGORIES, PRODUCT_STATUSES } from '@/types/api';

/**
 * 路线图 3.9 / 3.10 / 3.11 — ad-04 商品表单统一封装。
 *
 * <p>3.9 升级:zod schema 完整化(6 字段 + status),错误用 shadcn 标准样式;
 * description 用 textarea 替代 Input(简易版富文本,Sprint 4 升级 tiptap)。
 *
 * <p>3.10 多图上传(file input + preview + 主图标记 ★ + 移除按钮),走 3.6 后端
 * `POST /api/admin/uploads` multipart,成功后 url 入 images 数组。**主图 = images[0]**,
 * 拖拽排序留 Sprint 4(dnd-kit 升级)。
 *
 * <p>3.11 SKU 行内编辑(useFieldArray + zod schema 校验),依赖 3.8 后端 PUT
 * /api/admin/products/{id}/skus 端点(本批未实现,字段已挂;**接口走 ProductForm
 * 的 onSubmit 一次性回传整张 skus 列表,3.8 端点用 replaceSkus 接受**)。
 */
const productSchema = z.object({
  name: z.string().min(1, '请输入商品名称').max(100, '名称不超过 100 字符'),
  description: z.string().max(2000, '描述不超过 2000 字符').default(''),
  price: z.coerce.number().positive('价格必须大于 0'),
  stock: z.coerce.number().int('库存必须为整数').min(0, '库存不能为负数'),
  category: z.string().min(1, '请选择分类'),
  status: z.enum(PRODUCT_STATUSES as unknown as [string, ...string[]]).default('ACTIVE'),
  imageUrl: z.string().default(''),
  images: z
    .array(
      z.object({
        url: z.string().min(1, '图片 URL 不能为空'),
        isPrimary: z.boolean().default(false),
      }),
    )
    .max(9, '最多 9 张图片')
    .default([]),
  skus: z
    .array(
      z.object({
        id: z.string().optional(),
        name: z.string().min(1, 'SKU 名称必填').max(100, 'SKU 名称不超过 100 字符'),
        specs: z.record(z.string()).default({}),
        price: z.coerce.number().positive('SKU 价格必须大于 0'),
        stock: z.coerce.number().int().min(0, 'SKU 库存不能为负'),
        sortOrder: z.coerce.number().int().min(0).max(99).default(0),
      }),
    )
    .max(50, 'SKU 最多 50 个')
    .default([]),
});

export type ProductFormValues = z.infer<typeof productSchema>;

interface ProductFormProps {
  defaultValues?: Partial<ProductFormValues>;
  onSubmit: (values: ProductFormValues) => Promise<void> | void;
  onCancel: () => void;
  submitting: boolean;
  submitLabel: string;
}

export function ProductForm({ defaultValues, onSubmit, onCancel, submitting, submitLabel }: ProductFormProps) {
  const { register, handleSubmit, formState, setValue, watch, reset, control } = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: '',
      description: '',
      price: 0,
      stock: 0,
      category: '',
      status: 'ACTIVE',
      imageUrl: '',
      images: [],
      skus: [],
      ...defaultValues,
    },
  });
  const { fields: imageFields, append: appendImage, remove: removeImage } = useFieldArray({ control, name: 'images' });
  const { fields: skuFields, append: appendSku, remove: removeSku } = useFieldArray({ control, name: 'skus' });
  const category = watch('category');
  const status = watch('status');

  // 3.10 上传中状态 + 错误
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  useEffect(() => {
    if (defaultValues) {
      reset({
        name: defaultValues.name ?? '',
        description: defaultValues.description ?? '',
        price: defaultValues.price ?? 0,
        stock: defaultValues.stock ?? 0,
        category: defaultValues.category ?? '',
        status: defaultValues.status ?? 'ACTIVE',
        imageUrl: defaultValues.imageUrl ?? '',
        images: defaultValues.images ?? [],
        skus: defaultValues.skus ?? [],
      });
    }
  }, [defaultValues, reset]);

  /**
   * 3.10 多文件上传:file input onChange → FormData → POST /api/admin/uploads
   * → 响应中 url 入 images 数组。
   */
  async function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploadError(null);
    setUploading(true);
    try {
      const fd = new FormData();
      for (const f of Array.from(files)) fd.append('files', f);
      const accessToken = localStorage.getItem('seafood-admin-access');
      const res = await fetch('/api/admin/uploads', {
        method: 'POST',
        body: fd,
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: '上传失败' }));
        throw new Error(err.message || '上传失败');
      }
      const data = await res.json();
      const newImages = (data.files as Array<{ url: string }>).map((f, i) => ({
        url: f.url,
        // 第一张新文件若是第一张图,设为主图;否则非主图
        isPrimary: imageFields.length === 0 && i === 0,
      })) as Array<{ url: string; isPrimary: boolean }>;
      appendImage(newImages);
      // 同步 imageUrl 字段(主图 URL 兼容旧字段)
      const primary = newImages[0];
      if (newImages.length > 0 && primary && primary.isPrimary) {
        setValue('imageUrl', primary.url, { shouldValidate: false });
      }
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
      // 重置 input,允许重复选同一文件
      e.target.value = '';
    }
  }

  return (
    <form
      className="space-y-4"
      onSubmit={handleSubmit(async (values) => {
        // 第一张主图同步到 imageUrl(向后兼容)
        const primary = values.images.find((i) => i.isPrimary) ?? values.images[0];
        if (primary) values.imageUrl = primary.url;
        await onSubmit(values);
      })}
      noValidate
    >
      <div className="space-y-2">
        <Label htmlFor="product-name">商品名称</Label>
        <Input id="product-name" {...register('name')} aria-invalid={Boolean(formState.errors.name)} />
        {formState.errors.name ? (
          <p className="text-sm text-error">{formState.errors.name.message}</p>
        ) : null}
      </div>
      <div className="space-y-2">
        <Label htmlFor="product-description">描述</Label>
        {/* 3.9 简易版富文本:textarea,1d 预算不引 tiptap,Sprint 4 升级 */}
        <textarea
          id="product-description"
          {...register('description')}
          className="flex min-h-[100px] w-full rounded-md border border-border bg-surface px-3 py-2 text-sm placeholder:text-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-50"
          placeholder="商品描述(最多 2000 字符)"
        />
        {formState.errors.description ? (
          <p className="text-sm text-error">{formState.errors.description.message}</p>
        ) : null}
      </div>
      <div className="grid grid-cols-3 gap-4">
        <div className="space-y-2">
          <Label htmlFor="product-price">价格(元)</Label>
          <Input
            id="product-price"
            type="number"
            step="0.01"
            min="0"
            {...register('price', { valueAsNumber: true })}
            aria-invalid={Boolean(formState.errors.price)}
          />
          {formState.errors.price ? (
            <p className="text-sm text-error">{formState.errors.price.message}</p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="product-stock">默认库存</Label>
          <Input
            id="product-stock"
            type="number"
            min="0"
            {...register('stock', { valueAsNumber: true })}
            aria-invalid={Boolean(formState.errors.stock)}
          />
          {formState.errors.stock ? (
            <p className="text-sm text-error">{formState.errors.stock.message}</p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="product-status">状态</Label>
          <Select value={status} onValueChange={(v) => setValue('status', v as ProductFormValues['status'], { shouldValidate: true })}>
            <SelectTrigger id="product-status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PRODUCT_STATUSES.map((s: string) => (
                <SelectItem key={s} value={s}>
                  {s}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="product-category">分类</Label>
        <Select value={category} onValueChange={(v) => setValue('category', v, { shouldValidate: true })}>
          <SelectTrigger id="product-category" aria-invalid={Boolean(formState.errors.category)}>
            <SelectValue placeholder="选择分类" />
          </SelectTrigger>
          <SelectContent>
            {PRODUCT_CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>
                {c}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {formState.errors.category ? (
          <p className="text-sm text-error">{formState.errors.category.message}</p>
        ) : null}
      </div>

      {/* 3.10 多图上传 */}
      <div className="space-y-2">
        <Label>商品图片(最多 9 张,★ 为主图)</Label>
        <div className="grid grid-cols-3 gap-2">
          {imageFields.map((field, idx) => {
            const isPrimary = watch(`images.${idx}.isPrimary`);
            return (
              <div key={field.id} className="relative aspect-square rounded-md border border-border overflow-hidden">
                <img src={watch(`images.${idx}.url`)} alt="" className="w-full h-full object-cover" />
                <div className="absolute top-1 left-1">
                  {isPrimary ? (
                    <Badge className="bg-accent text-surface">★ 主图</Badge>
                  ) : (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        // 重置所有 isPrimary = false,设当前为 true
                        imageFields.forEach((_, i) => setValue(`images.${i}.isPrimary`, i === idx, { shouldValidate: false }));
                      }}
                    >设为主图</Button>
                  )}
                </div>
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  className="absolute top-1 right-1 h-6 px-2"
                  onClick={() => removeImage(idx)}
                >×</Button>
              </div>
            );
          })}
        </div>
        <Input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          onChange={handleFileSelect}
          disabled={uploading || imageFields.length >= 9}
        />
        {uploadError ? <p className="text-sm text-error">{uploadError}</p> : null}
        {uploading ? <p className="text-sm text-muted">上传中…</p> : null}
        {formState.errors.images ? (
          <p className="text-sm text-error">{(formState.errors.images as { message?: string })?.message ?? '图片列表错误'}</p>
        ) : null}
      </div>

      {/* 3.11 SKU 行内编辑 */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label>SKU 规格(可选,留空用默认 price/stock)</Label>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => appendSku({
              name: '',
              specs: {},
              price: 0,
              stock: 0,
              sortOrder: skuFields.length,
            })}
            disabled={skuFields.length >= 50}
          >+ 添加 SKU</Button>
        </div>
        {skuFields.map((field, idx) => (
          <div key={field.id} className="grid grid-cols-12 gap-2 items-end p-2 rounded-md border border-border">
            <div className="col-span-4 space-y-1">
              <Label htmlFor={`sku-name-${idx}`} className="text-xs">名称</Label>
              <Input id={`sku-name-${idx}`} {...register(`skus.${idx}.name`)} placeholder="如 200g 装" />
            </div>
            <div className="col-span-2 space-y-1">
              <Label htmlFor={`sku-price-${idx}`} className="text-xs">价格</Label>
              <Input id={`sku-price-${idx}`} type="number" step="0.01" {...register(`skus.${idx}.price`, { valueAsNumber: true })} />
            </div>
            <div className="col-span-2 space-y-1">
              <Label htmlFor={`sku-stock-${idx}`} className="text-xs">库存</Label>
              <Input id={`sku-stock-${idx}`} type="number" {...register(`skus.${idx}.stock`, { valueAsNumber: true })} />
            </div>
            <div className="col-span-3 space-y-1">
              <Label htmlFor={`sku-specs-${idx}`} className="text-xs">规格(逗号分隔)</Label>
              <Input
                id={`sku-specs-${idx}`}
                placeholder="净含量=200g,产地=青岛"
                onChange={(e) => {
                  const map: Record<string, string> = {};
                  e.target.value.split(',').forEach((p) => {
                    const [k, v] = p.split('=').map((s) => s?.trim());
                    if (k && v) map[k] = v;
                  });
                  setValue(`skus.${idx}.specs`, map, { shouldValidate: false });
                }}
              />
            </div>
            <div className="col-span-1">
              <Button type="button" variant="destructive" size="sm" onClick={() => removeSku(idx)}>×</Button>
            </div>
          </div>
        ))}
        {formState.errors.skus ? (
          <p className="text-sm text-error">{(formState.errors.skus as { message?: string })?.message ?? 'SKU 列表错误'}</p>
        ) : null}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          取消
        </Button>
        <Button type="submit" disabled={submitting || uploading}>
          {submitting ? '提交中…' : submitLabel}
        </Button>
      </div>
    </form>
  );
}

export default ProductForm;
