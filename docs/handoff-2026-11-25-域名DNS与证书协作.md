# Argus · 域名、DNS 与证书协作交接（历史归档）

> **状态：已被 2026-11-26 正式演示路线取代，当前不得按本文执行。**
>
> 当前路线是 Qoder Sites 前端 + `https://106.14.125.122/api`。发布根目录为 `Argus-replica`，静态目录为 `Argus-frontend/dist`，启用 SPA，更新现有站点，不新建站点。当前发布依据是仓库根目录 `.Argus 数据洞察中心.qoder.site`；`Argus-frontend/.Argus 数据洞察中心.qoder.site` 为旧快照，保留但不要手改或删除。

## 现行操作入口

- [Qoder Sites + IP API 发布与跨站登录清单](./https-setup.md)
- [公网入口傻瓜教程（现行 IP API 版）](./公网入口傻瓜教程-待同学填写.md)
- [StudyHack 计划版](./StudyHack-计划版.md)
- [StudyHack 待办清单](./StudyHack-待办清单.md)

## 历史背景

2026-11-25 曾计划由外网同学协助注册域名、配置 A 记录、研究 DNS-01/HTTP-01 证书并交付证书文件。该方案只反映当时的协作安排，不构成当前 G0 或演示发布的前置条件。

历史职责边界仍可作为安全参考：

- 服务器、Nginx、JAR、防火墙、数据库、ES、MinIO 和模型配置由项目负责人操作。
- 外网协作人员不接触 SSH、服务器账号或数据库/模型凭据。
- 密码、Token、证书私钥不得进入聊天、公开网盘或 Git 仓库。
- 任何付费域名、VPS 或证书服务仍需单独确认范围和成本。

## 当前禁止事项

- 不要为了当前演示重新采购域名、VPS 或证书服务。
- 不要把 DuckDNS、DNS-01、HTTP-01 或海外中继写成当前 API 地址。
- 不要把服务器 `root` 指向 `Argus-frontend/dist`；当前前端由 Qoder Sites 托管。
- 不要修改或删除两个 `.qoder.site` 描述文件。
