import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (
            id.includes('/node_modules/react/') ||
            id.includes('/node_modules/react-dom/') ||
            id.includes('/node_modules/scheduler/')
          ) {
            return 'react-vendor'
          }
          if (id.includes('/node_modules/react-router-dom/') || id.includes('/node_modules/react-router/')) {
            return 'router-vendor'
          }
          if (id.includes('/node_modules/antd/')) {
            return 'antd-vendor'
          }
          if (id.includes('/node_modules/@ant-design/icons/')) {
            return 'icons-vendor'
          }
          return 'vendor'
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 3001,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 3001,
  },
})
