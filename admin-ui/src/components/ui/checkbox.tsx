import * as React from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface CheckboxProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  checked?: boolean;
  onCheckedChange?: (checked: boolean) => void;
}

/**
 * shadcn 风格 Checkbox 组件(无 Radix 依赖,thin controlled component)。
 * 路线图 4.16 ad-05 DataTable 行选择用。
 *
 * <p>设计取舍:不引 @radix-ui/react-checkbox 包(本项目只在这一处用,引入 ~30KB gz 不值);
 * 直接用原生 {@code <input type="checkbox">} 包一个 styled label,父组件用 ref
 * 设 {@code .indeterminate} 表达"部分选中"(4.16 批量发货"全选"按钮需要)。
 */
const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, checked = false, disabled, onCheckedChange, ...props }, ref) => {
    return (
      <span className={cn('relative inline-flex h-4 w-4 items-center justify-center', className)}>
        <input
          ref={ref}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onCheckedChange?.(e.target.checked)}
          className="peer absolute inset-0 h-full w-full cursor-pointer appearance-none rounded border border-border bg-surface checked:border-accent checked:bg-accent disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          {...props}
        />
        <Check
          className={cn(
            'pointer-events-none h-3 w-3 text-white',
            checked ? 'opacity-100' : 'opacity-0'
          )}
        />
      </span>
    );
  }
);
Checkbox.displayName = 'Checkbox';

export { Checkbox };
