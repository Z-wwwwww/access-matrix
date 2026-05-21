import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    plugins: [vue(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      // 5273 = new frontend (legacy javaweb-ui-pms uses Vite's default 5173).
      port: 5273,
      proxy: {
        '/proxy_url': {
          target: env.VITE_API_BASE_URL || 'http://127.0.0.1:9135/api',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/proxy_url/, '')
        },
        '/zipcloud': {
          target: 'https://zipcloud.ibsnet.co.jp/api',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/zipcloud/, '')
        }
      }
    }
  }
})
