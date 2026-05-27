import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    /**
     * 路径别名：@ → src
     *
     * 作用：
     * - 后续目录分层（components/services/composables 等）时，减少相对路径 ../ 的维护成本；
     * - 便于跨目录引用，提升可读性与重构安全性。
     */
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    /**
     * 本地开发代理：
     * - 前端统一以 /api 作为请求前缀；
     * - dev 环境通过 Vite proxy 转发至后端服务（由 VITE_API_TARGET 控制，默认 http://localhost:8080）。
     *
     * 说明：
     * - 生产环境通常由 Nginx/网关将 /api 转发至后端；
     * - 该配置仅影响 `npm run dev` 的开发服务器。
     */
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    }
  }
})
