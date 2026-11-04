# 12b: 方面级评测脚本

**What to build:** 在标注集上跑评测，输出 per-aspect P/R/F1 + Cohen's κ + 与 LLM 一致率。论文实验章的主力数据。

**Blocked by:** 04, 07, 12a

**Status:** done

- [x] 在标注集上跑 L0/L2/L1 三方案的文档级评测
- [x] 输出 per-aspect Precision/Recall/F1（六个维度各自）
- [x] 不只报 micro-average，也报 macro-average
- [x] 计算规则系统与 LLM 的一致率（LLM 作 silver standard）
- [x] 误差分析：分歧处的具体案例
- [x] 论文红线：不把文档级准确率当作 ABSA 准确率；不声称关键词锚定达到它没有的召回率
- [x] 输出论文可引用的表格格式
