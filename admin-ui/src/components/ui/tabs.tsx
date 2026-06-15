import * as React from 'react';
import { cn } from '@/lib/utils';

export interface TabsProps {
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  className?: string;
  children: React.ReactNode;
}

/**
 * 路线图 4.16 ad-05:状态 tabs(全部 / 待支付 / 已付款 / …)。
 * 简化的 controlled / uncontrolled tabs:不引 @radix-ui/react-tabs 包,
 * 直接 controlled value + 回调,符合本组件单一使用场景。
 *
 * <p>设计取舍:Radix Tabs 包内有 keyboard navigation / focus management 行为
 * 4.16 不需要(本组件仅 client 端过滤列表,无表单交互),不引可省 ~25KB gz。
 */
export function Tabs({ value: controlledValue, defaultValue, onValueChange, className, children }: TabsProps) {
  // uncontrolled 模式不展开,本组件只做 controlled(订单列表页明确持有 statusTab state)
  const value = controlledValue;
  return (
    <TabsContext.Provider value={{ value: value ?? defaultValue ?? '', onValueChange }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  );
}

interface TabsContextValue {
  value: string;
  onValueChange?: (v: string) => void;
}
const TabsContext = React.createContext<TabsContextValue>({ value: '' });

export function TabsList({ className, children }: { className?: string; children: React.ReactNode }) {
  return (
    <div
      role="tablist"
      className={cn(
        'inline-flex h-10 items-center justify-start gap-1 rounded-md border border-border bg-surface p-1 text-muted',
        className
      )}
    >
      {children}
    </div>
  );
}

export interface TabsTriggerProps {
  value: string;
  className?: string;
  children: React.ReactNode;
}

export function TabsTrigger({ value, className, children }: TabsTriggerProps) {
  const ctx = React.useContext(TabsContext);
  const active = ctx.value === value;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      data-state={active ? 'active' : 'inactive'}
      onClick={() => ctx.onValueChange?.(value)}
      className={cn(
        'inline-flex h-8 items-center justify-center whitespace-nowrap rounded px-3 text-sm font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent',
        active
          ? 'bg-soft text-fg shadow-sm'
          : 'text-muted hover:text-fg',
        className
      )}
    >
      {children}
    </button>
  );
}
