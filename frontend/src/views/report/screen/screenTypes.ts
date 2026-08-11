import type { CSSProperties } from 'vue'

/** 大屏部件类型：metric 指标卡 / number 数字 / line 折线 / pie 饼图 / table 表格 */
export type ScreenWidgetType = 'metric' | 'number' | 'line' | 'pie' | 'table'

export interface ScreenWidget {
  id: string
  type: ScreenWidgetType
  title: string
  /** 数据源键：ReportScreenVo 的字段名（loginSuccessCount / loginTrend / operByModule / recentOpers …） */
  dataKey?: string
  /** 12 列网格坐标 */
  x: number
  y: number
  w: number
  h: number
}

export interface ScreenLayout {
  name: string
  theme: string
  widgets: ScreenWidget[]
}

export const GRID_COLS = 12
export const ROW_HEIGHT = 56
export const GRID_GAP = 10

/** 布局在画布网格中的定位样式（运行时渲染与设计器共用）。 */
export function gridStyle(widget: ScreenWidget): CSSProperties {
  return {
    gridColumnStart: widget.x + 1,
    gridColumnEnd: `span ${widget.w}`,
    gridRowStart: widget.y + 1,
    gridRowEnd: `span ${widget.h}`,
  }
}

/** 运行时默认布局：等价于原「经典大屏」的 4 指标卡 + 2 折线 + 5 饼图 + 1 表格。 */
export const DEFAULT_LAYOUT: ScreenLayout = {
  name: '经典大屏',
  theme: 'dark',
  widgets: [
    { id: 'm1', type: 'metric', title: '登录成功', dataKey: 'loginSuccessCount', x: 0, y: 0, w: 3, h: 2 },
    { id: 'm2', type: 'metric', title: '操作总量', dataKey: 'operTotal', x: 3, y: 0, w: 3, h: 2 },
    { id: 'm3', type: 'metric', title: '操作失败', dataKey: 'operErrorCount', x: 6, y: 0, w: 3, h: 2 },
    { id: 'm4', type: 'metric', title: 'AI 任务', dataKey: 'aiTaskCount', x: 9, y: 0, w: 3, h: 2 },
    { id: 'l1', type: 'line', title: '登录趋势', dataKey: 'loginTrend', x: 0, y: 2, w: 4, h: 4 },
    { id: 'l2', type: 'line', title: '操作趋势', dataKey: 'operTrend', x: 4, y: 2, w: 4, h: 4 },
    { id: 'p1', type: 'pie', title: '模块分布', dataKey: 'operByModule', x: 8, y: 2, w: 4, h: 4 },
    { id: 'p2', type: 'pie', title: '设备类型', dataKey: 'deviceByType', x: 0, y: 6, w: 4, h: 4 },
    { id: 'p3', type: 'pie', title: '设备状态', dataKey: 'deviceByStatus', x: 4, y: 6, w: 4, h: 4 },
    { id: 'p4', type: 'pie', title: '任务状态', dataKey: 'jobByStatus', x: 8, y: 6, w: 4, h: 4 },
    { id: 'p5', type: 'pie', title: 'AI 状态', dataKey: 'aiByStatus', x: 0, y: 10, w: 4, h: 4 },
    { id: 't1', type: 'table', title: '最近操作', dataKey: 'recentOpers', x: 4, y: 10, w: 8, h: 4 },
  ],
}

export const LAYOUT_STORAGE_KEY = 'screen-layout'

export function loadSavedLayout(): ScreenLayout | null {
  try {
    const raw = localStorage.getItem(LAYOUT_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as ScreenLayout
    if (Array.isArray(parsed.widgets)) {
      return parsed
    }
    return null
  } catch {
    return null
  }
}

export function saveLayout(layout: ScreenLayout): void {
  localStorage.setItem(LAYOUT_STORAGE_KEY, JSON.stringify(layout))
}
