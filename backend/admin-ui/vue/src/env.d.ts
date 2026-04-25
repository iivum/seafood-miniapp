/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module '@element-plus/icons-vue' {
  import type { DefineComponent } from 'vue'
  const icons: Record<string, DefineComponent<{}, {}, any>>
  export default icons
  export const iconsVue: Record<string, DefineComponent<{}, {}, any>>
}
