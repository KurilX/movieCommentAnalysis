# scripts — 验证脚本

答辩前的回归验证脚本。**依赖 `frontend/node_modules` 里的 playwright**，所以要用 `--prefix` 或从 frontend 目录调用。

## 前置

```powershell
# 服务必须先起来
<mysql>\bin\mysqld --console
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

## 用法

```powershell
cd scripts

# 1. 后端接口端到端验证（登录/总览/爬虫/雷达图/重跑/提交评论）
node verify-e2e.mjs

# 2. 前端页面渲染验证（截图到 docs/screenshots/）
node verify-pages.mjs

# 3. 后台功能验证（真实登录 + 三个管理页 + 提交评论）
node verify-admin.mjs
```

若报 `Cannot find package 'playwright'`，从 frontend 目录运行：

```powershell
cd frontend
node ../scripts/verify-e2e.mjs
```

## 脚本说明

| 脚本 | 验证内容 | 依赖 |
|---|---|---|
| `verify-e2e.mjs` | 8 项后端接口（管理员登录、数据总览、爬虫任务、雷达图样本量、代表句、一键重跑、提交评论） | 仅 Node，无 playwright |
| `verify-pages.mjs` | 5 个页面渲染 + 控制台错误检查 + 截图 | playwright |
| `verify-admin.mjs` | 真实登录后台 + 3 个管理页 + 提交评论，截图 | playwright |

## 预期输出

`verify-e2e.mjs` 全绿示例：

```
✅ 管理员登录 — role=ADMIN
✅ 数据总览 — 电影 20 / 评论 4000 / 已分析 4000
✅ 触发爬虫任务 — taskId=1
✅ 爬虫任务列表 — 1 个任务
✅ 雷达图数据 — 样本量 27/7/26/16（不应全为 0）
✅ 代表句 — 好评 10 条 / 差评 10 条
✅ 一键重跑 — count=4000        （约 4 秒，2026-11-04 批量插入后）
✅ 提交评论实时分析 — POSITIVE (90%)
```

**雷达图样本量若全为 0** → 说明 `PrecomputeRunner` 早于 `CsvDataImporter` 执行了，检查 `@Order` 注解。
