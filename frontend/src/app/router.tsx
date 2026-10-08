import type { RouteObject } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { CheckoutPage } from '../features/checkout/CheckoutPage'
import { CustomersPage } from '../features/customers/CustomersPage'
import { IntegrationPage } from '../features/integration/IntegrationPage'
import { OrderDetailPage } from '../features/orders/OrderDetailPage'
import { OrdersPage } from '../features/orders/OrdersPage'
import { ProductsPage } from '../features/products/ProductsPage'
import { AppLayout } from './AppLayout'
import { NotFound } from './NotFound'
import { RequireAuth } from './RequireAuth'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <CheckoutPage /> },
      { path: 'orders', element: <OrdersPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
      { path: 'customers', element: <CustomersPage /> },
      {
        path: 'products',
        element: (
          <RequireAuth roles={['ADMIN']}>
            <ProductsPage />
          </RequireAuth>
        ),
      },
      {
        path: 'integration',
        element: (
          <RequireAuth roles={['ADMIN']}>
            <IntegrationPage />
          </RequireAuth>
        ),
      },
      { path: '*', element: <NotFound /> },
    ],
  },
]
