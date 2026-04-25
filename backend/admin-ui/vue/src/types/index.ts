// Product types
export interface Product {
  id?: string
  name: string
  description: string
  price: number
  stock: number
  category: string
  imageUrl: string
  onSale: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CreateProductRequest {
  name: string
  description: string
  price: number
  stock: number
  category: string
  imageUrl: string
  onSale: boolean
}

// Order types
export interface OrderItem {
  id: string
  productId: string
  productName: string
  price: number
  quantity: number
  imageUrl?: string
}

export interface Address {
  city: string
  district: string
  address: string
  postalCode?: string
  receiverName: string
  phone: string
}

export interface OrderHistory {
  status: string
  timestamp: string
  note?: string
}

export interface Order {
  id: string
  userId: string
  orderNumber: string
  items: OrderItem[]
  totalPrice: number
  shippingFee: number
  discountAmount: number
  finalPrice: number
  status: string
  displayStatus: string
  createdAt: string
  paidAt?: string
  shippedAt?: string
  deliveredAt?: string
  shippingAddress: Address
  trackingNumber?: string
  carrierName?: string
  carrierCode?: string
  transactionId?: string
  note?: string
  orderHistory?: OrderHistory[]
}

export interface ShipOrderRequest {
  carrierCode: string
  carrierName: string
  trackingNumber: string
}

// User types
export interface User {
  id: string
  openId: string
  nickname: string
  avatarUrl?: string
  phone?: string
  role: string
  createdAt?: string
}

// Config types
export interface ConfigProperty {
  id?: string
  serviceName: string
  profile: string
  label: string
  key: string
  value: string
  encrypted: boolean
  updatedAt?: string
}

export interface SaveConfigRequest {
  serviceName: string
  profile: string
  label: string
  key: string
  value: string
  encrypted: boolean
}

// API response types
export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error?: string
  code?: string
}
