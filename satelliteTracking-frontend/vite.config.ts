import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { viteStaticCopy } from 'vite-plugin-static-copy'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const cesiumSource = 'node_modules/cesium/Build/Cesium'
const cesiumBaseUrl = 'cesiumStatic'
const cesiumPath = (subDir: string) =>
  path.join(fileURLToPath(new URL('.', import.meta.url)), cesiumSource, subDir)

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '')
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080'
  const enableHttps =
    env.VITE_DEV_USE_HTTPS === 'true' || process.env.VITE_DEV_USE_HTTPS === 'true'

  return {
    envDir: '..',
    define: {
      CESIUM_BASE_URL: JSON.stringify(`/${cesiumBaseUrl}`),
    },
    server: {
      https: enableHttps ? {} : undefined,
      proxy: {
        '/api': {
          target: devProxyTarget,
          changeOrigin: true,
        },
      },
    },
    plugins: [
      react(),
      ...(enableHttps ? [basicSsl()] : []),
      viteStaticCopy({
        targets: [
          { src: cesiumPath('Workers'), dest: cesiumBaseUrl },
          { src: cesiumPath('Assets'), dest: cesiumBaseUrl },
          { src: cesiumPath('Widgets'), dest: cesiumBaseUrl },
          { src: cesiumPath('ThirdParty'), dest: cesiumBaseUrl },
        ],
      }),
    ],
  }
})
