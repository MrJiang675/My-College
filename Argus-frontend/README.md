# Argus-frontend

Vue 3 + Vite 静态前端；保留原有界面、路由和认证边界，不在静态托管平台运行 Java 后端。

## Qoder Sites 前端预览

在本目录执行：

```sh
npm run build
npm run preview -- --host 127.0.0.1 --port 4173
```

完整构建会同时运行类型检查和 Vite 打包，输出目录为 `dist/`。不要直接双击 `dist/index.html`，请打开预览命令输出的 HTTP 链接。

未设置 `VITE_API_BASE_URL` 时，生产构建只提供前端预览：首页、登录/注册表单和页面导航可打开；不发送后端请求。页面会明确提示后端尚未接入，提交表单不会创建账号或伪造登录成功。工作区仍受真实认证保护，上传、问答和 AI 对话暂不可用。首页沿用的指标与功能介绍仅为原界面展示，不代表本次实测结果。

正式托管时，在 Qoder 中打开 `Argus-replica/Argus-frontend` 作为当前项目，再使用 Qoder Sites 发布。选择 `dist` 作为静态目录并启用 SPA 回退（`spa: true`），保留现有 history 路由；发布后需验证直接访问 `/login` 和深链接刷新。不上传项目根目录、后端代码、`node_modules` 或环境文件。当前没有配置云端数据库、Function 或替代 Java 后端。

## 接入真实 HTTPS 后端

参考 `.env.example`，在本目录创建已被 `*.local` 规则忽略的 `.env.production.local`：

```dotenv
VITE_API_BASE_URL=https://example.duckdns.org
```

示例域名不是可用服务，必须替换为实际后端域名。也可填写以 `/api` 或 `/api/` 结尾的 HTTPS 地址；构建时统一为一个 `/api` 前缀，Axios 和两处 SSE 使用相同地址。生产构建会拒绝 HTTP 地址、内嵌凭据、查询参数和错误路径，防止 Mixed Content 或错误 API 路由。

修改地址后必须重新执行 `npm run build` 并重新发布，静态页面不会读取服务器运行时环境变量。所有 `VITE_*` 值会进入公开的浏览器代码，禁止放入密码、API Key、JWT 密钥或 DuckDNS Token。

登录、刷新和退出请求已启用 `withCredentials: true`；SSE 继续使用 Bearer Token。后端恢复联调后，仍需人工配置 HTTPS、准确的 CORS Origin、`Access-Control-Allow-Credentials: true` 和 `HttpOnly; Secure; SameSite=None` 的刷新 Cookie。第三方 Cookie 拦截可能影响跨站会话，本轮未验证这些后端能力。

本地 `npm run dev` 未设置 API 变量时默认仍通过 `/api` 代理；显式设置 `VITE_API_BASE_URL=` 则仅预览前端，不访问后端。`VITE_DEV_PROXY_TARGET` 可在 `.env.local` 中设置，仅用于开发，不会成为 Qoder Sites 的线上代理。

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we replace the `tsc` CLI with `vue-tsc` for type checking. In editors, we need [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) to make the TypeScript language service aware of `.vue` types.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Type-Check, Compile and Minify for Production

```sh
npm run build
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```
