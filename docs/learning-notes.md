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


## 2026-11-28：工作台材料包和动态绘图

### 安全 Markdown：解析与净化是两件事

以前直接插值会显示 Markdown 符号；直接 v-html AI 文本又会引入 XSS。现在统一组件先转义原始 HTML，marked 仅生成 Markdown 结构，再用标签/协议白名单清理。链接只允许 http/https/mailto，删掉事件属性；不加载 AI 回复中的远程图片。

```vue
<!-- 修改前 -->
<p>{{ turn.a }}</p>
<!-- 修改后：净化逻辑集中于 safeMarkdown.ts -->
<MarkdownContent :content="turn.a" />
```

### 递归下降与判别联合类型

`kind` 区分内置 `values` 与公式的 `expression/params`，必须先判断 kind，才能读对应字段。表达式语法分 add → mul → unary → power → primary，幂右结合；一元负号低于幂，所以 `-2^2=-4`。函数表既限制名称也限制参数个数。不能用 eval/new Function 执行 AI 输出；语法正确仍可能有奇点，故再做区间采样和有限值检查。

```ts
// 修改前：只接受三个内置数组模型
// type Model = { kind:'rc'|'beam'|'normal'; values:number[] }
// 修改后：内置与通用公式分别建模
// type Model = {kind:BuiltinKind;values:number[]} | FormulaModel
const fn = compileFormula('A * exp(-k * x) * cos(w * x)', ['x','A','k','w'])
const y = fn({ x:1, A:1, k:0.2, w:3 })
```

采样通过不等于物理正确：单位、边界条件、忽略空气阻力等假设仍须告知。峰谷 HUD 是采样峰谷，不冒充严格解析极值。

### SSRF 与 DNS 重绑定

新增抓取接口比普通 URL 格式检查要求更高。必须检查 DNS 所有地址，拒绝私网/回环/链路本地/保留地址；实际 socket 也必须使用检查过的 IP，不能检查一次后让 HTTP 库再解析不同地址。HTTPS 固定 IP 连接仍按原域名验证证书与 SNI；重定向每跳重新验证。并发、超时和响应体上限共同保护低配服务器。本机 198.18.* 虚拟映射被拒绝是正确结果，不能为演示取消过滤。

### 浏览器 File、Vue 响应式与持久化边界

`shallowReactive(new Map<string,File>())` 保持本地文件对象，不放进工作台 state。state 只存材料要求、显示名、确认元数据；File ID 是临时关联，刷新后文件不在 Map 就必须显示缺失，不能根据旧勾选声称备齐。

```ts
// 错误思路：提交材料送给 extract-text 或转 Base64 保存到工作台
// 正确边界：分析来源可上传，真正的提交文件仅放本地 Map
files.set(localId, file)
const issues = blockers(requirement, files)
// 没文件/扩展名不符/未人工确认 => 禁止 ZIP
```

### ZIP/OOXML 与“改扩展名”误区

ZIP STORE 包含本地头、CRC-32、中央目录和结束记录；UTF-8 文件名需置通用标志位 11。XLSX 是包含 `[Content_Types].xml`、`_rels/.rels`、workbook、worksheet 等的 ZIP；内部关系路径不能被扁平化或把 `.rels` 的前导点删除。CSV 改名为 PDF 不会改变内容结构，修复应重新导出正确格式或替换为真结构文件。

### 渐进式状态机与异步保存

不要用“目标非空”直接推导当前步骤，否则快捷填入目标会在用户确认前跳页。步骤应独立保存，确认事件才推进；旧状态只在初次加载时推断一次。每个申请方向单独保存，明确通用建议不等于完成证据对标。跳面试前等待防抖保存完成，否则新页面从后端读取的可能还是旧目标/旧经历。
