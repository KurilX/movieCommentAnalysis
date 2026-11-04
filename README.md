# 基于方面级情感分析的电影评论分析系统

本科毕业设计。论文题目：**《基于方面级情感分析的电影评论分析系统设计与实现》**。

当前状态：**系统可运行、可演示**。20 部电影 / 4000 条真实豆瓣影评已落库，情感分析、方面雷达图、关键词、提交评论实时分析、后台管理全部可用。

## 快速开始

```bash
# 1. 启动 MySQL（免安装版，见 docs/mysql安装步骤.md）
#    公司电脑无法开虚拟化 → 不用 Docker
C:\Users\zepeng.huang\Downloads\mysql-26.7.0-winx64\mysql-26.7.0-winx64\bin\mysqld --console

# 2. 启动后端（:8081，会自动建表 + 导入 4000 条数据 + 预计算分析结果）
cd backend
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 · 后台账号 `admin` / `admin123` · 健康检查 `http://localhost:8081/actuator/health`

**首次启动约需 3 分钟**（导入 4000 条评论 + 预计算 4000 条分析 + 24000 条方面记录）。之后重启秒级；改完模型后点「一键重跑全库」约 4 秒。

## 目录结构

| 目录 | 用途 |
|---|---|
| `backend/` | Spring Boot 3.2 + MyBatis-Plus + MySQL 后端 |
| `frontend/` | Vue 3 + Element Plus + ECharts 前端 |
| `ml/` | Python 离线训练与 ONNX 导出管线（**预留，暂未启用**） |
| `data/` | 影评数据与生成脚本（**必须提交 Git**） |
| `docs/` | 决策记录、环境安装、状态审计 |
| `.scratch/` | Spec 与工单 |

## 技术栈

- **后端**：Java 17（**必须 Temurin 或 Zulu**，否则 ONNX Runtime 抛 `UnsatisfiedLinkError`）+ Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + MySQL 8+
- **前端**：Vue 3 + Element Plus + ECharts
- **AI 推理**：ONNX Runtime Java 1.31.0 + DJL tokenizers 0.38.0（**推理期零 Python**）
- **中文预处理**：HanLP 1.x `portable-1.8.6`
- **LLM**：阿里百炼 DashScope Java SDK 2.23.2（L1，未接入）

> Redis 已移除：公司电脑无虚拟化权限，且缓存用 MySQL 表替代同样有效。

## 已实现

- L0 规则情感引擎（382 词情感词典 + 否定词 + 程度副词），Accuracy **0.7698**
- 三分类派生（置信度间隔法 + 规则信号）
- 方面级情感分析（四维：剧情/演技/画面特效/配乐音效，贝叶斯平滑）
- 关键词提取（HanLP 分词 + 真 TF-IDF）
- 评测框架（Accuracy / macro-F1 / 混淆矩阵 / θ 敏感性分析）
- 用户体系（JWT + 双角色）
- 8 个前端页面 + 后台管理

## 未实现（P1/P2）

- L2 ONNX 模型**未导出**（`ml/models` 不存在，4 个推理测试跳过）
- L1 大模型**未接入**（需 API Key）
- **L0 Accuracy 0.7698 未达 0.85 目标**（瓶颈在语境消歧，不在词典规模；见审计文档）
- "节奏""台词"两维覆盖率低于 5%，暂未展示
- 标注集（300-500 条）未建

## 核心决策

见 [docs/决策记录.md](docs/决策记录.md)（5 轮 grilling 的完整决策链 + 研究结论）。
Spec 与工单见 [.scratch/](.scratch/)。

## 创新点

1. **纯 Java 通过 ONNX Runtime 部署微调中文 Transformer，推理期零 Python**（研究验证的空白）
2. **方面级细粒度情感分析**：自建中文电影评价维度体系
3. **二分类→三分类派生方法** + θ 敏感性分析
4. **中文电影领域情感词典构建**
