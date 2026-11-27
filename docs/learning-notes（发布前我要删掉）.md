# 项目学习笔记

> **部署说明（2026-11-26）**：本文 11/23 的 Nginx `root /srv/argus/Argus-frontend/dist` 仅为历史同源部署示例，不是当前配置。当前前端由 Qoder Sites 托管 `Argus-frontend/dist` 并启用 SPA，浏览器调用 `https://106.14.125.122/api`；发布从 `Argus-replica` 根目录使用当前 `.qoder.site` 更新现有站点，不新建站点。旧 DuckDNS/DNS-01 方案不再作为当前部署配置。

## 2026-11-23 · 一次页面刷新、一次 API 调用和一次上传

**同源策略**看协议、主机、端口。80 端口页面直接访问 10001 端口属于跨域；页面请求相对地址 /api 时，浏览器与 Nginx 同源，Nginx 到 Spring 的转发发生在服务器之间。

与公网分支原配置对照：

```java
// 原配置按具体来源匹配
.allowedOrigins(allowedOrigins).allowCredentials(true)

// 本次配置支持具体来源或模式
.allowedOriginPatterns(allowedOrigins).allowCredentials(true)
```

浏览器不接受带凭据请求的响应使用 Access-Control-Allow-Origin: *。Spring 匹配来源模式后回显具体 Origin，并允许凭据。默认模式 * 会信任任意来源，只适合开发演示。生产应列出实际页面来源。跨域请求还需要客户端启用 credentials，cookie 也须满足 SameSite、Secure 等发送条件。

**Nginx 路由**。此前 master 没有本次使用的站点入口。新增配置的核心如下：

```nginx
server {
    listen 80;
    root /srv/argus/Argus-frontend/dist;
    client_max_body_size 110m;
    location / { try_files $uri $uri/ /index.html; }
    location /api/ { proxy_pass http://localhost:10001; }
}
```

server 选择站点，location 选择处理路径。刷新前端深层路由时，磁盘上通常没有对应文件，try_files 最终返回 index.html，由 Vue Router 接手。/api/ 由更具体的前缀匹配；proxy_pass 只有主机与端口，因此上游仍收到 /api/ 开头的路径。超过 Nginx 请求体上限会在抵达 Spring 前收到 413，增加该上限后 Spring 的单文件和总请求限制仍生效。

**构建与类型检查**。本仓库的 npm run build 通过 run-p 并行运行 vue-tsc --build 和 vite build。前者检查 TypeScript 与 Vue 模板中的类型，后者打包浏览器资源，在 dist 中生成 index.html 和 assets 下的 JS、CSS 等文件。即使 dist 已存在，也应检查整个命令的退出码。缺少 run-p 时两个子任务都未启动，这个错误不能用于判断源码能否通过类型检查。

**本次 TS2532 实例**。三个小组页签的日期回退分支此前在两次 split 后直接访问 [0]，类型检查将数组索引结果视为可能的 undefined。下面两行分别是修复前后的表达式：

```ts
String(dateStr).split(' ')[0].split('T')[0].replace(/-/g, '.')
dateStr.replace(/[ T][\s\S]*$/, '').replace(/-/g, '.')
```

例如，无法按标准日期解析的输入 abc-defT12:00 仍显示 abc.def。第一次构建中 Vite 已生成 dist，但 vue-tsc 退出码为 2；修复后三个组件均通过类型检查，整个 build 退出码为 0。

## 2026-11-23 · 跨源预检、跨站 Cookie 与流式传输

携带 Authorization 或使用 application/json 的跨源请求通常先发 OPTIONS。浏览器提供 Origin、预期方法和头；预检通过后才发实际请求。凭据模式下 Allow-Origin 必须是具体来源。allowedOriginPatterns 匹配配置列表并回显来源；带凭据的生产服务应只信任自己控制的前端。

```java
// 原方法列表显式枚举；现在让 Spring 回应实际预检方法和头，缓存一小时
.allowedOriginPatterns(allowedOrigins).allowedMethods("*").allowedHeaders("*")
.maxAge(3600).allowCredentials(true)
```

**SameSite 与 CSRF。** Strict 仅同站携带；Lax 还允许部分跨站顶层安全导航；None 允许跨站子请求，浏览器要求同时设置 Secure。原先的 .sameSite(SAME_SITE_POLICY) 固定为 Lax，现在使用 .sameSite(authProperties.getRefreshCookieSameSite())。HttpOnly 限制脚本读取，不能防止浏览器自动附带 cookie。放开 SameSite 后，对基于 cookie 的状态变更应考虑 CSRF token 或来源校验；CORS 本身无法阻止所有跨站请求。

```nginx
# 原配置已关闭代理缓冲；增加上游空闲超时，避免长对话被过早断开。
proxy_buffering off;
proxy_read_timeout 300s;
client_max_body_size 110m;
```

SSE 可以在同一个 HTTP 响应内逐个发送事件。关闭代理缓冲可及时传递已由应用 flush 的数据。上传限制作用于请求方向；超限会在抵达 Spring 前收到 413，提高 Nginx 上限仍需遵守后端单文件和总请求大小限制。
