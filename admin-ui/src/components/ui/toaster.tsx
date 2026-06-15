import * as React from 'react';
import { create } from 'zustand';
import { cn } from '@/lib/utils';

type ToastVariant = 'default' | 'success' | 'error' | 'warning';

interface Toast {
  id: string;
  message: string;
  variant: ToastVariant;
}

interface ToastStore {
  toasts: Toast[];
  push: (message: string, variant?: ToastVariant) => void;
  dismiss: (id: string) => void;
}

export const useToastStore = create<ToastStore>((set) => ({
  toasts: [],
  push: (message, variant = 'default') => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    set((s) => ({ toasts: [...s.toasts, { id, message, variant }] }));
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, 4000);
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

const variantClasses: Record<ToastVariant, string> = {
  default: 'border-border bg-surface text-fg',
  success: 'border-success/30 bg-success-soft text-success',
  error: 'border-error/30 bg-error-soft text-error',
  warning: 'border-warning/30 bg-warning-soft text-warning',
};

/** Convenience hook for components — `toast.success(msg)`, `toast.error(msg)`. */
export function useToast() {
  const push = useToastStore((s) => s.push);
  return React.useMemo(
    () => ({
      show: (msg: string, variant?: ToastVariant) => push(msg, variant),
      success: (msg: string) => push(msg, 'success'),
      error: (msg: string) => push(msg, 'error'),
      warning: (msg: string) => push(msg, 'warning'),
    }),
    [push],
  );
}

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);
  return (
    <div className="fixed bottom-4 right-4 z-toast flex flex-col gap-2" role="status" aria-live="polite">
      {toasts.map((t) => (
        <button
          key={t.id}
          type="button"
          onClick={() => dismiss(t.id)}
          className={cn(
            'rounded-md border px-4 py-2 text-left text-base shadow-md transition-colors',
            variantClasses[t.variant],
          )}
        >
          {t.message}
        </button>
      ))}
    </div>
  );
}
