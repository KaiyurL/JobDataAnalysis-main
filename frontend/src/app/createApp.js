import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from '@/App.vue'
import router from '@/app/router/index.js'
import '@/style.css'

/**
 * 应用装配入口（createApp）
 *
 * 设计目标：
 * - 将应用级装配（UI 框架、路由、全局图标、全局样式）下沉到 src/app；
 * - main.js 仅负责调用 createApplication 并 mount，入口更清晰；
 * - 保持与原初始化行为一致（Element Plus + 全量 Icons 注册）。
 *
 * @returns {import('vue').App} Vue App 实例
 */
export const createApplication = () => {
  const app = createApp(App)

  app.use(ElementPlus)
  app.use(router)

  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  return app
}
