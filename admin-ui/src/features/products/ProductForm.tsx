import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { PRODUCT_CATEGORIES } from '@/types/api';

const productSchema = z.object({
  name: z.string().min(1, '请输入商品名称'),
  description: z.string().default(''),
  price: z.coerce.number().positive('价格必须大于 0'),
  stock: z.coerce.number().int('库存必须为整数').min(0, '库存不能为负数'),
  category: z.string().min(1, '请选择分类'),
  imageUrl: z.string().default(''),
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
  const { register, handleSubmit, formState, setValue, watch, reset } = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: '',
      description: '',
      price: 0,
      stock: 0,
      category: '',
      imageUrl: '',
      ...defaultValues,
    },
  });
  const category = watch('category');

  useEffect(() => {
    if (defaultValues) {
      reset({
        name: defaultValues.name ?? '',
        description: defaultValues.description ?? '',
        price: defaultValues.price ?? 0,
        stock: defaultValues.stock ?? 0,
        category: defaultValues.category ?? '',
        imageUrl: defaultValues.imageUrl ?? '',
      });
    }
  }, [defaultValues, reset]);

  return (
    <form
      className="space-y-4"
      onSubmit={handleSubmit(async (values) => {
        await onSubmit(values);
      })}
      noValidate
    >
      <div className="space-y-2">
        <Label htmlFor="product-name">商品名称</Label>
        <Input id="product-name" {...register('name')} aria-invalid={Boolean(formState.errors.name)} />
        {formState.errors.name ? (
          <p className="text-small text-feedback-error">{formState.errors.name.message}</p>
        ) : null}
      </div>
      <div className="space-y-2">
        <Label htmlFor="product-description">描述</Label>
        <Input id="product-description" {...register('description')} />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="product-price">价格</Label>
          <Input
            id="product-price"
            type="number"
            step="0.01"
            min="0"
            {...register('price', { valueAsNumber: true })}
            aria-invalid={Boolean(formState.errors.price)}
          />
          {formState.errors.price ? (
            <p className="text-small text-feedback-error">{formState.errors.price.message}</p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="product-stock">库存</Label>
          <Input
            id="product-stock"
            type="number"
            min="0"
            {...register('stock', { valueAsNumber: true })}
            aria-invalid={Boolean(formState.errors.stock)}
          />
          {formState.errors.stock ? (
            <p className="text-small text-feedback-error">{formState.errors.stock.message}</p>
          ) : null}
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
          <p className="text-small text-feedback-error">{formState.errors.category.message}</p>
        ) : null}
      </div>
      <div className="space-y-2">
        <Label htmlFor="product-image">图片 URL</Label>
        <Input id="product-image" {...register('imageUrl')} placeholder="https://..." />
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          取消
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting ? '提交中…' : submitLabel}
        </Button>
      </div>
    </form>
  );
}

export default ProductForm;
