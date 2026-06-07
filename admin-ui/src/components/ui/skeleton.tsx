import { cn } from '@/lib/utils';

/** Lightweight skeleton — used for dashboard loading state (spec scenario). */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('animate-pulse rounded-md bg-app-divider', className)} {...props} />;
}
