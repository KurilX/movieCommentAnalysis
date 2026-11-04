# 05: L2 本地 ONNX 深度模型

**What to build:** Python 一次性导出 Erlangshen-Roberta-110M-Sentiment → ONNX → int8 量化；Java 侧用 ONNX Runtime 加载并作为 SentimentAnalyzer 实现跑通，离线可用。创新点 1 的实物。

**Blocked by:** 01, 03

**Status:** partial（分析器已实现，ml/models 未导出，4 个测试跳过）

- [ ] `ml/` 下 ONNX 导出脚本（Python，可复制粘贴即运行，含数据下载、导出、量化、验证）
- [ ] 导出 `IDEA-CCNL/Erlangshen-Roberta-110M-Sentiment`（apache-2.0）为 ONNX
- [ ] int8 动态量化，模型文件约 100 MB
- [ ] Java 侧用 ONNX Runtime Java 1.31.0 + DJL tokenizers 0.38.0 加载并推理
- [ ] 作为 `SentimentAnalyzer` 的实现类（L2），输出二分类正/负概率
- [ ] Windows 环境（Temurin/Zulu 17）下不抛 `UnsatisfiedLinkError`
- [ ] CPU 推理延迟在可接受范围（记录实测值，论文用）
- [ ] 纯 Java 推理，推理期零 Python
