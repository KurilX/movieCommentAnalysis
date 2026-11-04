# AGENTS.md

## 项目

基于方面级情感分析的电影评论分析系统 —— 本科毕业设计（论文题目同名）。
Java 后端 + Vue 前端，做中文影评的方面级情感分析与可视化。

## 怎么跑

```powershell
# MySQL（免安装版，非 Docker）
C:\Users\zepeng.huang\Downloads\mysql-26.7.0-winx64\mysql-26.7.0-winx64\bin\mysqld --console

cd backend && mvn spring-boot:run    # :8081，首次约 3 分钟（导入+预计算）
cd frontend && npm run dev           # :5173
```

后台账号 `admin` / `admin123`。健康检查 `curl localhost:8081/actuator/health`。

## 技术栈

- 后端：Java 17（**必须 Temurin/Zulu**，否则 ONNX Runtime 报 `UnsatisfiedLinkError`）+ Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8+
- 前端：Vue 3 + Element Plus + ECharts
- 无 Redis、无 Docker（开发机无虚拟化权限）

## 目录与约定

| 目录 | 说明 |
|---|---|
| `backend/` | Spring Boot 后端，按功能分包（`sentiment`/`aspect`/`evaluation`/`movie`/`user`） |
| `frontend/` | Vue 3 前端，页面在 `src/pages/`，接口集中在 `src/api.js` |
| `ml/` | Python 离线管线（ONNX 导出，**预留未启用**） |
| `data/` | 影评 CSV + 生成脚本，**必须提交 Git** |
| `docs/` | 决策记录、环境、状态审计 |
| `.scratch/` | Spec 与工单（本地 issue tracker） |

## 硬约束（会踩坑的）

1. **`SentimentAnalyzer.analyze()` 是全系统唯一测试接缝**。测 AI 逻辑一律走这个接口，不测内部实现。
2. **启动顺序不可调换**：`CsvDataImporter(@Order(1))` → `PrecomputeRunner(@Order(2))` → `AdminSeeder(@Order(3))`。预计算早于导入会看到 0 条评论而跳过，雷达图全为 0。`StartupOrderTest` 守着这条。
3. **演示路径绝不依赖实时网络请求**。海报用本地 SVG，爬虫用 mock，禁止任何外部 URL。
4. **`sampleCount` 字段名**：雷达图接口必须用 `sampleCount`（前端读的名字），不是 `mentionedCount`。
5. **MySQL 26.x 不支持 `CREATE INDEX IF NOT EXISTS`**，索引在 `CREATE TABLE` 内用 `KEY` 声明。
6. **CSV 数据源无电影元数据**：导演/演员/年份/简介全为空，前端已做占位处理。

## 当前状态与下一步

**已完成**：L0 规则引擎、三分类派生、方面级分析（四维）、关键词、评测框架、用户体系、8 个前端页面、后台管理。**181 个测试通过**（4 个跳过）。

**2026-11-04 完成的 P1 项**：
- **情感词典**：从 43 词硬编码搬到 `backend/src/main/resources/sentiment-lexicon.json`，扩到 **382 词**（含影视领域词）。改词表只改 JSON，不需要动 Java 代码。
- **L0 规则**：新增三条 —— 无信号默认倾向、中性标记词让位、褒贬混合判中性。Accuracy 0.3740 → **0.7698**（三组归因数字见审计文档）。
- **关键词**：接入 HanLP portable（离线），权重补全为真正的 TF×IDF。词云不再有 `the`/纯数字/未分词长串。
- **一键重跑**：改多行 VALUES 批量插入（单批 500 行），**180 秒 → 4~5 秒**。
- **可选完整版分词**：后台数据管理页有下载按钮，未下载时自动降级 portable（演示永远走 portable）。

**未完成**：
- L2 ONNX 模型**未导出**（`ml/models` 不存在，4 个测试跳过）
- L1 大模型**未接入**（需 API Key）
- **L0 Accuracy 0.7698 未达 spec 的 0.85 验收线**（瓶颈在语境消歧，不在词典规模；详见审计文档 3.1 节）
- 「节奏」「台词」两维度覆盖率仍低于 5% 阈值，未替换

详见 `docs/项目状态审计.md`。

