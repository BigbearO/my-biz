import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
    base: './',
    server: {
        port: 3008,
        host: "0.0.0.0",
        open: true,
        // proxy: {
        //     "/api": {
        //         target:
        //             "https://mock.mengxuegu.com/mock/64102492fe77f949bc0d6d83/demo",
        //         changeOrigin: true,
        //         rewrite: (path) => path.replace(/^\/api/, ""),
        //     },
        // },
    },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
