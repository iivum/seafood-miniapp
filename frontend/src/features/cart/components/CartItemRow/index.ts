import type { CartItem } from '../../types';

export interface CartItemRowProps {
  item: CartItem;
  readonly?: boolean;
}
