<template>
  <div class="site">
    <header class="site-header">
      <div class="container header-inner">
        <a class="brand" href="#top">
          <span class="brand-mark">Y15</span>
          <span class="brand-name">Y15智能管理平台</span>
        </a>
        <button class="nav-toggle" aria-label="菜单" @click="navOpen = !navOpen">
          <Menu v-if="!navOpen" :size="22" />
          <X v-else :size="22" />
        </button>
        <nav class="site-nav" :class="{ open: navOpen }">
          <a href="#features">产品能力</a>
          <a href="#product">平台体验</a>
          <a href="#solutions">解决方案</a>
          <a href="#pricing">定价</a>
          <a class="nav-cta" href="#contact" @click="trackEvent('nav_demo_click')">预约演示</a>
        </nav>
      </div>
    </header>

    <main id="top">
      <section class="hero">
        <img class="hero-visual" src="/src/assets/dashboard-preview.svg" alt="Y15智能管理平台管理后台预览" />
        <div class="hero-overlay" />
        <div class="container hero-content">
          <p class="eyebrow">企业生产管理 · 双端智能平台</p>
          <h1>Y15智能管理平台</h1>
          <p class="hero-lead">
            一个平台覆盖组织权限、流程审批、AI 任务、实时监控与多租户管理，让企业管理从经验驱动升级为数据驱动。
          </p>
          <div class="hero-actions">
            <a class="btn btn-primary" href="#contact" @click="trackEvent('hero_trial_click')">免费试用</a>
            <a class="btn btn-ghost" href="#product" @click="trackEvent('hero_product_click')">查看平台</a>
          </div>
          <div class="hero-metrics">
            <div><strong>15+</strong><span>核心模块</span></div>
            <div><strong>99.9%</strong><span>可用性目标</span></div>
            <div><strong>3 端</strong><span>管理/官网/AI</span></div>
          </div>
        </div>
      </section>

      <section class="trust-band">
        <div class="container trust-inner">
          <span>已为成长型企业提供</span>
          <strong>RBAC 权限</strong>
          <strong>数据权限</strong>
          <strong>多租户</strong>
          <strong>流程引擎</strong>
          <strong>AI 编排</strong>
        </div>
      </section>

      <section id="features" class="section features">
        <div class="container">
          <div class="section-heading">
            <p class="eyebrow">产品能力</p>
            <h2>从组织管理到智能决策，一站到位</h2>
            <p>围绕企业生产管理的真实场景，把复杂能力沉淀为开箱即用的模块。</p>
          </div>
          <div class="feature-grid">
            <article v-for="feature in features" :key="feature.title" class="feature-card">
              <component :is="feature.icon" :size="26" :stroke-width="1.8" />
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.desc }}</p>
            </article>
          </div>
        </div>
      </section>

      <section id="product" class="section product">
        <div class="container">
          <div class="section-heading">
            <p class="eyebrow">平台体验</p>
            <h2>后台管理系统，为运营者而设计</h2>
            <p>信息密度、操作效率与安全边界并重，适合高频重复的日常工作。</p>
          </div>
          <div class="product-showcase">
            <img src="/src/assets/dashboard-preview.svg" alt="Y15后台管理系统界面" />
            <div class="product-points">
              <div v-for="point in productPoints" :key="point.title" class="point">
                <CheckCircle2 :size="22" />
                <div>
                  <h3>{{ point.title }}</h3>
                  <p>{{ point.desc }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="solutions" class="section solutions">
        <div class="container">
          <div class="section-heading">
            <p class="eyebrow">解决方案</p>
            <h2>面向不同组织形态的管理方案</h2>
          </div>
          <div class="solution-grid">
            <article v-for="solution in solutions" :key="solution.title" class="solution-card">
              <span class="solution-index">{{ solution.index }}</span>
              <h3>{{ solution.title }}</h3>
              <p>{{ solution.desc }}</p>
              <ul>
                <li v-for="item in solution.items" :key="item">{{ item }}</li>
              </ul>
            </article>
          </div>
        </div>
      </section>

      <section id="pricing" class="section pricing">
        <div class="container">
          <div class="section-heading">
            <p class="eyebrow">定价</p>
            <h2>按发展阶段选择，随时升级</h2>
          </div>
          <div class="pricing-grid">
            <article v-for="plan in pricing" :key="plan.name" class="pricing-card" :class="{ popular: plan.popular }">
              <h3>{{ plan.name }}</h3>
              <p class="price"><strong>{{ plan.price }}</strong><span> / {{ plan.unit }}</span></p>
              <p class="plan-desc">{{ plan.desc }}</p>
              <ul>
                <li v-for="item in plan.items" :key="item">{{ item }}</li>
              </ul>
              <a class="btn btn-primary" href="#contact">立即咨询</a>
            </article>
          </div>
        </div>
      </section>

      <section id="contact" class="section contact">
        <div class="container contact-grid">
          <div class="contact-copy">
            <p class="eyebrow">预约演示</p>
            <h2>30 分钟了解 Y15 是否适合你</h2>
            <p>提交需求后，顾问会结合你的组织规模和业务流程，给出部署建议与演示方案。</p>
            <div class="contact-info">
              <Phone :size="20" />
              <span>400-800-0015</span>
            </div>
            <div class="contact-info">
              <Mail :size="20" />
              <span>hello@y15-platform.com</span>
            </div>
          </div>
          <form class="contact-form" @submit.prevent="submit">
            <h3>获取专属方案</h3>
            <label>
              <span>姓名</span>
              <input v-model="form.name" required placeholder="请输入姓名" />
            </label>
            <label>
              <span>企业名称</span>
              <input v-model="form.company" required placeholder="请输入企业名称" />
            </label>
            <label>
              <span>手机号</span>
              <input v-model="form.phone" required placeholder="请输入手机号" />
            </label>
            <label>
              <span>业务需求</span>
              <textarea v-model="form.message" rows="4" placeholder="简单描述你的管理场景" />
            </label>
            <button class="btn btn-primary btn-block" type="submit">
              <ArrowRight :size="18" />
              提交需求
            </button>
            <p v-if="submitted" class="submit-tip">已收到需求，顾问将尽快与你联系。</p>
          </form>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <div class="container footer-inner">
        <div>
          <span class="brand-mark">Y15</span>
          <p>企业智能管理平台，后台管理系统 + 前台官网一体化交付。</p>
        </div>
        <div class="footer-links">
          <a href="#features">产品能力</a>
          <a href="#product">平台体验</a>
          <a href="#pricing">定价</a>
          <a href="#contact">联系我们</a>
        </div>
        <p class="copyright">© 2026 Y15智能管理平台</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import {
  Activity,
  ArrowRight,
  BrainCircuit,
  CheckCircle2,
  LockKeyhole,
  Mail,
  Menu,
  Phone,
  PlugZap,
  ShieldCheck,
  Workflow,
  X,
} from 'lucide-vue-next'
import { trackEvent } from './analytics'

const navOpen = ref(false)
const submitted = ref(false)
const form = reactive({ name: '', company: '', phone: '', message: '' })

const features = [
  { icon: ShieldCheck, title: '组织与权限', desc: '用户、角色、菜单、按钮和数据权限统一管理，支持部门与岗位组织模型。' },
  { icon: Workflow, title: '智能流程引擎', desc: '流程定义、审批节点、待办任务与审批日志，多级审批按角色流转。' },
  { icon: BrainCircuit, title: 'AI 任务编排', desc: 'Java 业务端与 Python AI 服务协同，任务状态机、回调重试与幂等机制开箱即用。' },
  { icon: Activity, title: '实时监控告警', desc: '服务器、SQL、定时任务与业务指标实时监控，告警通过站内通知实时推送。' },
  { icon: LockKeyhole, title: '多租户隔离', desc: '租户、用户和文件数据隔离，配合数据权限满足集团与服务机构场景。' },
  { icon: PlugZap, title: '开放工程基线', desc: 'Excel 导入导出、MinIO 文件管理、OpenAPI、CI/CD 与 Helm 部署能力完整。' },
]

const productPoints = [
  { title: '信息密度优先', desc: '表格、筛选、标签页与操作入口紧凑排布，减少重复点击。' },
  { title: '流程全程可追踪', desc: '每个审批节点都有操作人、意见和时间记录，随时回查。' },
  { title: '中英文与暗黑模式', desc: '后台支持中英文切换与主题切换，适配不同团队习惯。' },
]

const solutions = [
  {
    index: '01',
    title: '中小企业管理',
    desc: '以较低成本获得完整管理基线，快速覆盖权限、流程、监控与 AI。',
    items: ['快速上线', '本地或私有化部署', '按需扩展模块'],
  },
  {
    index: '02',
    title: '集团与多组织',
    desc: '通过多租户和数据权限，让不同事业部在统一平台内独立运营。',
    items: ['租户级数据隔离', '集团统一菜单', '角色与数据范围分级'],
  },
  {
    index: '03',
    title: '服务商交付',
    desc: '开放接口与文档齐全，适合作为 Vibe Coding 和二次开发脚手架。',
    items: ['OpenAPI 契约', 'Helm 交付', '冒烟与压测脚本'],
  },
]

const pricing = [
  {
    name: '标准版',
    price: '¥2,980',
    unit: '年',
    desc: '适合单组织快速起步',
    items: ['1 个租户', '基础组织与权限', '通知公告与日志', '社区支持'],
  },
  {
    name: '专业版',
    price: '¥9,800',
    unit: '年',
    desc: '适合成长型业务团队',
    popular: true,
    items: ['5 个租户', '流程引擎与 AI 编排', '实时监控与告警', '专属实施支持'],
  },
  {
    name: '旗舰版',
    price: '定制',
    unit: '项目',
    desc: '适合集团与私有化交付',
    items: ['不限租户', '私有化/混合云部署', '定制开发与培训', 'SLA 保障'],
  },
]

function submit() {
  trackEvent('lead_submit', { company: form.company })
  submitted.value = true
}
</script>
