# assets

前端静态资源目录，用于存放图片、图标和全局样式资源。

- 页面背景与示例图片：放在本目录并在组件中引用
- 业务图标：优先使用 `@ant-design/icons-vue`，避免手工维护 SVG
- 全局样式：`main.ts` 统一引入，Ant Design Vue 组件通过 `unplugin-vue-components` 按需加载

