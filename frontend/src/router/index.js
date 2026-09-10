import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/reading-area'
  },
  {
    path: '/reading-area',
    name: 'ReadingArea',
    component: () => import('../views/ReadingArea.vue')
  },
  {
    path: '/capacity-dashboard',
    name: 'CapacityDashboard',
    component: () => import('../views/CapacityDashboard.vue')
  },
  {
    path: '/desk-chair',
    name: 'DeskChair',
    component: () => import('../views/DeskChair.vue')
  },
  {
    path: '/tag',
    name: 'Tag',
    component: () => import('../views/Tag.vue')
  },
  {
    path: '/batch-transfer',
    name: 'BatchTransfer',
    component: () => import('../views/BatchTransfer.vue')
  },
  {
    path: '/change-log',
    name: 'ChangeLog',
    component: () => import('../views/ChangeLog.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router