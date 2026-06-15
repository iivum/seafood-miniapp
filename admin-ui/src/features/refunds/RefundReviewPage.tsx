import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, X } from 'lucide-react';
import { refundsApi } from './api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useToast } from '@/components/ui/toaster';
import { formatDateTime, formatPrice } from '@/lib/utils';
import type { RefundResponse, RefundStatusCode } from '@/types/api';

const STATUS_VARIANT: Record<RefundStatusCode, 'warning' | 'success' | 'destructive'> = {
  REQUESTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'destructive',
};

const STATUS_LABEL: Record<RefundStatusCode, string> = {
  REQUESTED: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
};

type TabValue = 'REQUESTED' | 'APPROVED' | 'REJECTED';
const TABS: { value: TabValue; label: string }[] = [
  { value: 'REQUESTED', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
];

export function RefundReviewPage() {
  const [tab, setTab] = useState<TabValue>('REQUESTED');
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['refunds', tab],
    queryFn: () => refundsApi.list({ status: tab, page: 0, size: 20 }),
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => refundsApi.approve(id),
    onSuccess: () => {
      toast.success('已同意退款');
      void queryClient.invalidateQueries({ queryKey: ['refunds'] });
    },
    onError: (err: Error) => toast.error(err.message ?? '同意失败'),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      refundsApi.reject(id, reason),
    onSuccess: () => {
      toast.success('已拒绝退款');
      setRejectingId(null);
      setRejectReason('');
      void queryClient.invalidateQueries({ queryKey: ['refunds'] });
    },
    onError: (err: Error) => toast.error(err.message ?? '拒绝失败'),
  });

  function startReject(id: string) {
    setRejectingId(id);
    setRejectReason('');
  }

  function confirmReject() {
    if (!rejectingId) return;
    if (rejectReason.trim().length === 0) {
      toast.error('请填写拒绝原因');
      return;
    }
    if (rejectReason.length > 200) {
      toast.error('拒绝原因不超过 200 字符');
      return;
    }
    rejectMutation.mutate({ id: rejectingId, reason: rejectReason.trim() });
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">退款审核</h1>
        <p className="text-sm text-muted">处理用户退款申请 — 同意或拒绝,拒绝需填原因</p>
      </div>

      <Tabs
        value={tab}
        onValueChange={(v: string) => setTab(v as TabValue)}
      >
        <TabsList>
          {TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <Card>
        <CardHeader>
          <CardTitle>{STATUS_LABEL[tab]}列表</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-2 p-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-10" />
              ))}
            </div>
          ) : isError || !data ? (
            <div className="p-6 text-center text-muted">加载失败</div>
          ) : data.content.length === 0 ? (
            <div className="p-6 text-center text-muted">暂无{STATUS_LABEL[tab]}的退款单</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>退款单号</TableHead>
                  <TableHead>关联订单</TableHead>
                  <TableHead>用户</TableHead>
                  <TableHead className="text-right">金额</TableHead>
                  <TableHead>原因</TableHead>
                  <TableHead>提交时间</TableHead>
                  <TableHead className="w-44">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((r) => (
                  <RefundRow
                    key={r.id}
                    refund={r}
                    onApprove={() => approveMutation.mutate(r.id)}
                    onReject={() => startReject(r.id)}
                    approving={approveMutation.isPending && approveMutation.variables === r.id}
                    rejecting={rejectMutation.isPending && rejectingId === r.id}
                  />
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {rejectingId ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          onClick={(e) => {
            if (e.target === e.currentTarget && !rejectMutation.isPending) {
              setRejectingId(null);
            }
          }}
        >
          <Card className="w-full max-w-md">
            <CardHeader>
              <CardTitle>拒绝退款</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="text-sm text-muted">拒绝原因(必填,≤ 200 字符)</label>
                <textarea
                  className="mt-1 w-full min-h-24 rounded-md border border-border bg-surface p-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  maxLength={200}
                  placeholder="例如:已签收 7 天,超售后期"
                />
                <div className="mt-1 text-right text-xs text-muted">
                  {rejectReason.length} / 200
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <Button
                  variant="outline"
                  onClick={() => setRejectingId(null)}
                  disabled={rejectMutation.isPending}
                >
                  取消
                </Button>
                <Button
                  variant="destructive"
                  onClick={confirmReject}
                  disabled={rejectMutation.isPending}
                >
                  {rejectMutation.isPending ? '提交中...' : '确认拒绝'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  );
}

function RefundRow({
  refund,
  onApprove,
  onReject,
  approving,
  rejecting,
}: {
  refund: RefundResponse;
  onApprove: () => void;
  onReject: () => void;
  approving: boolean;
  rejecting: boolean;
}) {
  return (
    <TableRow>
      <TableCell className="font-mono text-sm">{refund.id}</TableCell>
      <TableCell className="font-mono text-sm text-muted">{refund.orderId}</TableCell>
      <TableCell className="text-muted">{refund.userId}</TableCell>
      <TableCell className="text-right">{formatPrice(refund.amount)}</TableCell>
      <TableCell className="max-w-xs truncate" title={refund.reason}>{refund.reason}</TableCell>
      <TableCell className="text-muted">{formatDateTime(refund.createdAt)}</TableCell>
      <TableCell>
        {refund.status === 'REQUESTED' ? (
          <div className="flex gap-1">
            <Button
              size="sm"
              variant="default"
              onClick={onApprove}
              disabled={approving || rejecting}
            >
              <Check className="mr-1 h-3.5 w-3.5" />
              {approving ? '同意中' : '同意'}
            </Button>
            <Button
              size="sm"
              variant="destructive"
              onClick={onReject}
              disabled={approving || rejecting}
            >
              <X className="mr-1 h-3.5 w-3.5" />
              拒绝
            </Button>
          </div>
        ) : (
          <Badge variant={STATUS_VARIANT[refund.status]}>{STATUS_LABEL[refund.status]}</Badge>
        )}
      </TableCell>
    </TableRow>
  );
}

export default RefundReviewPage;
