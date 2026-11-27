import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    port: 5220,
    proxy: {
      '/api': {
        target: 'http://localhost:5221',
        changeOrigin: true
      },
      '/files': {
        target: 'http://localhost:5221',
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1500
  }
})
