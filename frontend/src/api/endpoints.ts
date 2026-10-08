import { api, query } from './client'
import type {
  CreateOrderInput,
  Customer,
  CustomerInput,
  LoginResponse,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PaymentMethod,
  Product,
  ProductInput,
} from './types'

export interface ProductFilters {
  q?: string
  active?: boolean
  page?: number
  size?: number
}

export interface CustomerFilters {
  q?: string
  page?: number
  size?: number
}

export interface OrderFilters {
  status?: OrderStatus
  from?: string
  to?: string
  number?: number
  page?: number
  size?: number
}

export const authApi = {
  login: (email: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { email, password }),
}

export const productsApi = {
  list: (filters: ProductFilters) => api.get<Page<Product>>(`/products${query({ ...filters })}`),
  create: (input: ProductInput) => api.post<Product>('/products', input),
  update: (id: string, input: ProductInput) => api.put<Product>(`/products/${id}`, input),
}

export const customersApi = {
  list: (filters: CustomerFilters) =>
    api.get<Page<Customer>>(`/customers${query({ ...filters })}`),
  create: (input: CustomerInput) => api.post<Customer>('/customers', input),
  update: (id: string, input: CustomerInput) => api.put<Customer>(`/customers/${id}`, input),
}

export const ordersApi = {
  list: (filters: OrderFilters) => api.get<Page<OrderSummary>>(`/orders${query({ ...filters })}`),
  get: (id: string) => api.get<Order>(`/orders/${id}`),
  create: (input: CreateOrderInput) => api.post<Order>('/orders', input),
  pay: (id: string, paymentMethod: PaymentMethod) =>
    api.post<Order>(`/orders/${id}/pay`, { paymentMethod }),
  cancel: (id: string) => api.post<Order>(`/orders/${id}/cancel`),
  refund: (id: string) => api.post<Order>(`/orders/${id}/refund`),
}
