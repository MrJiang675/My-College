import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:10001'
  const configuredBaseUrl = env.VITE_API_BASE_URL?.trim()
  let apiBaseUrl = command === 'serve' && (configuredBaseUrl === undefined || configuredBaseUrl === '/api')
    ? '/api'
    : ''

  if (configuredBaseUrl && !(command === 'serve' && configuredBaseUrl === '/api')) {
    let endpoint: URL
    try {
      endpoint = new URL(configuredBaseUrl)
    } catch {
      throw new Error('VITE_API_BASE_URL 必须是完整的后端 HTTPS 地址；未接入后端时请留空。')
    }
    if (endpoint.protocol !== 'https:' && !(command === 'serve' && endpoint.protocol === 'http:')) {
      throw new Error('生产构建的 VITE_API_BASE_URL 必须使用 HTTPS，避免 Mixed Content。')
    }
    if (endpoint.username || endpoint.password || endpoint.search || endpoint.hash) {
      throw new Error('VITE_API_BASE_URL 不得包含凭据、查询参数或片段。')
    }
    const pathname = endpoint.pathname.replace(/\/+$/, '')
    if (pathname !== '' && pathname !== '/api') {
      throw new Error('VITE_API_BASE_URL 只接受后端域名或以 /api 结尾的地址。')
    }
    endpoint.pathname = '/api'
    apiBaseUrl = endpoint.toString()
  }

  return {
    define: {
      'import.meta.env.VITE_API_BASE_URL': JSON.stringify(apiBaseUrl),
    },
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
          selfHandleResponse: false,
          ws: true,
          configure: (proxy) => {
            proxy.on('proxyRes', (proxyRes) => {
              const contentType = proxyRes.headers['content-type'] ?? ''
              if (typeof contentType === 'string' && contentType.includes('text/event-stream')) {
                proxyRes.headers['cache-control'] = 'no-cache, no-transform'
                proxyRes.headers['x-accel-buffering'] = 'no'
                delete proxyRes.headers['content-length']
              }
            })
          },
        },
      },
    },
  }
})
