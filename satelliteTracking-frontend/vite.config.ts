import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { viteStaticCopy } from 'vite-plugin-static-copy'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const cesiumSource = 'node_modules/cesium/Build/Cesium'
const cesiumBaseUrl = 'cesiumStatic'
const cesiumPath = (subDir: string) =>
  path.join(fileURLToPath(new URL('.', import.meta.url)), cesiumSource, subDir)

// https://vite.dev/config/
export default defineConfig({
  envDir: '..',
  define: {
    CESIUM_BASE_URL: JSON.stringify(`/${cesiumBaseUrl}`),
  },
  plugins: [
    react(),
    viteStaticCopy({
      targets: [
        { src: cesiumPath('Workers'), dest: cesiumBaseUrl },
        { src: cesiumPath('Assets'), dest: cesiumBaseUrl },
        { src: cesiumPath('Widgets'), dest: cesiumBaseUrl },
        { src: cesiumPath('ThirdParty'), dest: cesiumBaseUrl },
      ],
    }),
  ],
})
