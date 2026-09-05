/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

// 官网运行期配置（见 .env.production / 部署环境注入），未配置时各模块按演示模式降级：
// - VITE_LEAD_ENDPOINT：预约表单线索提交端点，为空则表单禁用并提示演示模式
// - VITE_CONTACT_PHONE / VITE_CONTACT_EMAIL：联系方式，为空则不渲染虚构占位
// - VITE_COMPANY_NAME：页脚/版权展示的企业名，为空回退产品名
// - VITE_GA_ID / VITE_GTM_ID：埋点标识，为空则 analytics 保持 no-op 不加载脚本
interface ImportMetaEnv {
  readonly VITE_LEAD_ENDPOINT?: string
  readonly VITE_CONTACT_PHONE?: string
  readonly VITE_CONTACT_EMAIL?: string
  readonly VITE_COMPANY_NAME?: string
  readonly VITE_GA_ID?: string
  readonly VITE_GTM_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
