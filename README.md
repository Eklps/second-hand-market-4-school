# 大学城闲置星球 (University Planet) - 项目全景文档

> **项目定位**：基于 Spring Boot + Redis + Nginx 的高性能大学城二手交易与社交平台，支持千万级 QPS 的高并发秒杀与实时聊天。

---

## 🚀 技术亮点 (Technical Highlights)

*   **独创 Redis Cluster 级秒杀方案**：
    - 使用 **Lua 脚本** 保证扣减库存与订单创建的原子性。
    - 引入 **Hash Tag `{ }`** 设计，确保所有相关 Key 路由至 Redis 集群同一 Slot，解决分布式跨槽（CROSSSLOT）报错。
    - **逻辑解耦**：将 XADD 消息队列写操作从 Lua 迁移至 Java 层异步处理，最大化系统吞吐量。
*   **全栈交互现代化**：
    - 前端重构：实现**仿抖音风格**的高保真登录弹窗（Login Modal）与交互动效。
    - **WebSocket 实时通信**：原生协议实现点对点全双工聊天、消息持久化与多维未读提示。
*   **企业级安全工程**：
    - **JWT + Spring Security**：支持令牌黑名单（Redis Blacklist）的安全退出机制。
    - **全局统一响应与异常处理**：内置 Result 封装类与 HandlerInterceptor 全局鉴权。

---

## 📂 项目结构 (Project Structure)

```text
e:\DianPing\secondHandMarket
├── run-app.ps1            # 【核心】一键全自动启动脚本 (Redis/Java/Nginx)
├── README.md              # 项目主文档
├── 2handMarket            # 后端 Spring Boot 工程
│   ├── src/main/java       # Java 源码 (Controller, Service, Mapper, Utils)
│   ├── src/main/resources  # 配置文件与 Lua 脚本 (seckill.lua)
│   └── pom.xml             # 项目依赖管理 (Spring Boot 2.7.4, MyBatis-Plus)
├── nginx4market           # 前端及反向代理工程
│   ├── html/hmdp           # 静态 HTML/JS/CSS (Vue/Element 驱动)
│   ├── conf/nginx.conf     # Nginx 代理配置 (8080 端口转发 8081)
│   └── nginx.exe           # Windows Nginx 服务程序

```

---

## 🛠️ 本地调试与启动方式

### 方法 A：推荐方案 (一键启动)
项目根目录下集成了自动化 PowerShell 脚本，可快速拉起全链环境：
1.  **前提**：确保安装了 Docker Desktop、Maven 和 Java 8/17。
2.  **执行**：
    ```powershell
    # 为当前窗口授权
    Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process
    # 启动全链
    .\run-app.ps1
    ```
    *该脚本会自动按序：启动 Redis 容器 -> Maven 编译 -> 弹窗运行后端 Jar -> 重启 Nginx。*

### 方法 B：手动方案
1.  **中间件**：启动 Redis (6379, 必须)、MySQL (3306)。
2.  **后端**：运行 `VoucherApplication` 主类 (监听 8081)。
3.  **前端**：双击启动 `nginx4market/nginx.exe` (监听 8080)。
4.  **访问**：浏览器打开 [http://localhost:8080](http://localhost:8080)。

---

## 📊 核心接口说明 (Core APIs)

| 模块 | 关键路径 | 说明 |
| :--- | :--- | :--- |
| **用户** | `/user/code` / `/user/login` | 验证码生成、登录/注册 (手机号+验证码) |
| **商品** | `/product/{id}` / `/product/list` | 商品详情展示、商铺列表分类展示 |
| **秒杀** | `/voucher-order/seckill` | 结合分布式锁与消息队列的极速抢购 |
| **社交** | `/post/blog` / `/post/like` | 笔记发布、点赞统计、Feed 流推送 |
| **消息** | `/chat/history` / `/chat/list` | WebSocket 消息历史记录、对话列表同步 |
| **关注** | `/follow/{id}` / `/follow/or/not` | 用户关系管理、共同关注查询 |

---

## ⚠️ 调试注意事项
1.  **Nginx 代理**：如果前端页面报错 `502 Bad Gateway`，请检查后端 8081 端口是否成功启动。
2.  **数据库**：核心表 `tb_user`, `tb_voucher`, `tb_voucher_order` 是业务命脉，请在 `application.yml` 中配置正确的 MySQL 连接。
3.  **日志查看**：后端 Jar 启动后会弹窗显示实时日志，建议配合 `nginx4market/logs/error.log` 排查前端转发问题。

---
*Created with ❤️ by Antigravity AI Assistant.*
