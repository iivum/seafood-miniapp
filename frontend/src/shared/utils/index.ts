/**
 * Format helpers used across features. Centralized so currency / date
 * formatting stays consistent.
 */

export function formatYuan(amount: number): string {
  if (typeof amount !== 'number' || Number.isNaN(amount)) return '¥ 0.00';
  return `¥ ${amount.toFixed(2)}`;
}

export function formatDate(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
}
