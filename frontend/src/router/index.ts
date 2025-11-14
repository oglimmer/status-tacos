import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/callback',
      name: 'callback',
      component: () => import('../views/CallbackView.vue'),
    },
    {
      path: '/monitors',
      name: 'monitors',
      component: () => import('../views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/alert-contacts',
      name: 'alert-contacts',
      component: () => import('../views/AlertContactsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/tenants',
      name: 'tenants',
      component: () => import('../views/TenantsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
    },
    {
      path: '/imprint',
      name: 'imprint',
      component: () => import('../views/ImprintView.vue'),
    },
    {
      path: '/tos',
      name: 'tos',
      component: () => import('../views/TosView.vue'),
    },
    {
      path: '/auth-debug',
      name: 'auth-debug',
      component: () => import('../views/AuthDebugView.vue'),
    },
    {
      path: '/monitor-debug',
      name: 'monitor-debug',
      component: () => import('../views/MonitorDebugView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  if (to.name === 'callback') {
    return next()
  }

  await authStore.initAuth()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return next({ name: 'home' })
  }

  next()
})

export default router
