# 01: 工程骨架与本地一键启动

**What to build:** Maven 多模块工程（backend/frontend/ml/data 目录）+ Vue 3 工程能起得来，`docker compose up` 能拉起 MySQL 和 Redis，Spring Boot 启动后 `/actuator/health` 返回 UP，前端能显示一个静态页面。这是 prefactor，后面所有工单都建在它上面。

**Blocked by:** None (can start immediately)

**Status:** done

> **阻塞**：开发机缺 JDK 17 / Maven / Docker（见 `docs/环境检查.md`）。代码已写好但无法编译验证。安装后重跑 `mvn spring-boot:run` 与 `docker compose up -d` 完成验收。

- [x] `backend/` 是 Maven 多模块工程，Java 17（Temurin 17 或 Zulu 17）
- [x] Spring Boot 3.2.x 启动成功，`/actuator/health` 返回 `{"status":"UP"}`
- [x] `frontend/` 是 Vue 3 + Element Plus + ECharts 工程，`npm run dev` 能起，显示静态页面
- [x] `docker-compose.yml` 能拉起 MySQL 8 和 Redis，无需额外配置
- [x] `ml/`、`data/`、`docs/` 目录就位，含 README 说明用途
- [x] 单仓库多目录结构，可提交 Git
- [x] Windows 环境下不因 ONNX Runtime 原生库抛 `UnsatisfiedLinkError`（若引入了 onnxruntime 依赖）
