# 05: 提交评论实时分析

**What to build:** 已登录用户在提交页输入一条评论，立刻看到情感分析结果（极性、置信度、触发关键词、六维方面）。这是答辩现场唯一有交互冲击力的环节。

**Blocked by:** 02

**Status:** done

- [x] `POST /api/comments/analyze`（body: `{ text, model }`）返回 `{ result: {...}, taskId }`
- [x] `result` 含 `polarity`、`confidence`、`triggerWords`、`aspects`
- [x] 分析**实时**执行（调用 `SentimentAnalyzer`），不走预计算
- [x] 提交的评论连同分析结果落库（`comment` + `comment_analysis` + `comment_aspect`）
- [x] 空文本返回 400
- [x] 未登录返回 401
- [x] `GET /api/comments/result/{taskId}` 返回 `{ result: {...} }`（为 L1 异步预留）
- [x] 提交页能显示结果和触发词解释
- [x] 测试：MockMvc 测提交接口（成功、空文本 400、未登录 401、落库验证）
