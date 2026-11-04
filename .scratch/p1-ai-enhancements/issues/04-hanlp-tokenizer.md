# 04: 关键词提取接入 HanLP portable 分词

**What to build:** 关键词提取从「按空格硬切」改成先做真正的中文分词再统计，词云不再出现未分词长串（「然而痛心的是」）、英文停用词（`the`/`and`）和纯数字。默认走 portable 版（纯 Java 单 jar、词典内置、完全离线），保证演示路径不依赖网络。

**Blocked by:** None (can start immediately)

**Status:** done

- [x] 引入 HanLP portable（纯 Java、核心词典内置、**无需联网下载数据包**）
- [x] 关键词提取先分词再统计词频，不再用标点替换+空格切分
- [x] 分词只服务关键词路径；L0 情感分析仍用现有 `contains` 匹配（保持「扩词」与「分词」可分别归因）
- [x] 输出过滤纯数字、纯英文停用词、单字虚词
- [x] `KeywordExtractor.extract(texts, topN)` 契约不变：仍返回 `word` / `weight` / `count` 三元组，`topN` 上限仍生效，空输入仍抛 `IllegalArgumentException`
- [x] 测试：含 `the`/`and`/`12` 的语料，输出中不含这些 token
- [x] 测试：中文语料能切出预期词（如「剧情」「演技」「特效」），且不再出现跨词长串
- [x] 测试：`portable` 在完全离线环境下可用（不触发任何网络请求）
- [x] 实测并记录改造前后同一部电影的词云样例（各一组真实输出），写进 `docs/项目状态审计.md`
