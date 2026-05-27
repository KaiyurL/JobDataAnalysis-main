import * as jobs from './services/modules/jobs.js'
import * as dataManagement from './services/modules/dataManagement.js'
import * as config from './services/modules/config.js'
import * as auth from './services/modules/auth.js'
import * as user from './services/modules/user.js'
import * as resume from './services/modules/resume.js'
import * as pipeline from './services/modules/pipeline.js'
import * as rag from './services/modules/rag.js'

/**
 * API 聚合出口（兼容层）
 *
 * 目的：
 * - 保留默认导出，作为历史代码的兼容入口；
 * - 内部按领域拆分为 services/modules/*，降低单文件耦合，便于后续扩展与维护；
 * - 让“接口定义”集中在 services 层，页面组件只关注 UI 与交互。
 *
 * 注意：
 * - 此处仅做对象展开聚合，不改变各函数的入参与返回值；
 * - 若未来需要引入更严格的类型约束，可优先在各 domain module 内补充类型与数据解包逻辑。
 */
export default Object.freeze({
  ...jobs,
  ...dataManagement,
  ...config,
  ...auth,
  ...user,
  ...resume,
  ...pipeline,
  ...rag
})
