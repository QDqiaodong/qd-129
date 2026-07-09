import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: Number(process.env.FRONTEND_PORT || 8229),
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || `http://localhost:${process.env.BACKEND_PORT || 8329}`,
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: Number(process.env.FRONTEND_PORT || 8229),
    strictPort: true
  }
})
