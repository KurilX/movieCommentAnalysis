# 04: 评测框架（接缝的批量模式）

**What to build:** 能加载测试集，跑 L0，输出 Accuracy/F1/混淆矩阵报告。后续所有对比实验的基础设施。

**Blocked by:** 03

**Status:** done

- [x] 测试集加载器（支持公开数据集 + 自建标注集）
- [x] 批量调用 `SentimentAnalyzer` 并记录结果
- [x] 计算 Accuracy、Precision、Recall、F1（macro/micro）
- [x] 输出混淆矩阵
- [x] 支持 per-aspect P/R/F1 计算
- [x] 输出对比报告（多方案在同一测试集上的指标对比表）
- [x] 评测结果可导出为论文可引用的表格格式
