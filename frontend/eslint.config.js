import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import parserVue from 'vue-eslint-parser'
import tseslint from 'typescript-eslint'

export default [
  {
    // dist/node_modules 由 ESLint 默认忽略；coverage/storybook-static 为本地构建产物，
    // 本地未清理时 `eslint .` 会扫到（CI 因 lint 先于 build 未暴露），显式忽略保证本地与 CI 一致
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'storybook-static/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...tseslint.configs.recommended,
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: parserVue,
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
      },
    },
  },
  {
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
]
