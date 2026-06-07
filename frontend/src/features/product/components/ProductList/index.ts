import type { Product } from '../../types';

export interface ProductListProps {
  products: Product[];
  loading?: boolean;
  error?: string;
}
