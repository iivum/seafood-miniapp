/**
 * Cart feature: types.
 */
export interface CartItem {
  productId: string;
  quantity: number;
  selected: boolean;
  addedAt: string;
}

export interface Cart {
  id: string;
  userId: string;
  items: CartItem[];
  totalQuantity: number;
  totalSelectedQuantity: number;
  selectedAmount: number;
  updatedAt: string;
}

export interface AddToCartRequest {
  productId: string;
  quantity: number;
}

export interface UpdateCartItemRequest {
  productId: string;
  quantity: number;
}
