import { afterEach, describe, expect, it } from 'vitest'
import { mountWithPlugins, normalizeText } from '@/test/testUtils'
import ModalForm from '@/components/ModalForm.vue'

// antd Modal 通过 teleport 渲染到 body，测试需在 body 上查找
function bodyText(): string {
  return document.body.textContent || ''
}

function clickButton(matcher: (text: string) => boolean) {
  const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('.ant-modal button'))
  const btn = buttons.find((b) => matcher(normalizeText(b.textContent || '')))
  if (!btn) throw new Error('未找到匹配按钮')
  btn.click()
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('ModalForm', () => {
  it('渲染标题与插槽内容', () => {
    const wrapper = mountWithPlugins(ModalForm, {
      props: { open: true, title: '新增用户' },
      slots: { default: '<input class="name-field" />' },
    })
    expect(bodyText()).toContain('新增用户')
    expect(document.querySelector('.name-field')).toBeTruthy()
    expect(wrapper.exists()).toBe(true)
  })

  it('点击确定触发 ok 事件', () => {
    const wrapper = mountWithPlugins(ModalForm, { props: { open: true, title: 'T' } })
    clickButton((t) => t.includes('确认'))
    expect(wrapper.emitted('ok')).toHaveLength(1)
  })

  it('点击取消触发 update:open=false', () => {
    const wrapper = mountWithPlugins(ModalForm, { props: { open: true, title: 'T' } })
    clickButton((t) => t.includes('取消'))
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('关闭时不渲染弹窗', () => {
    mountWithPlugins(ModalForm, { props: { open: false, title: 'T' } })
    expect(document.querySelector('.ant-modal')).toBeNull()
  })
})
