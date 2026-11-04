# 03: 方面雷达图 + 好评差评代表句

**What to build:** 详情页显示四维方面雷达图（剧情/演技/画面特效/配乐音效）和好评/差评代表句（带置信度和触发关键词）。这是答辩演示的主战场。

**Blocked by:** 02

**Status:** done

- [x] 预计算：对每条评论跑 `RuleBasedAspectAnalyzer`，结果写入 `comment_aspect` 纵表
- [x] `GET /api/movies/{id}/analyze` 返回 `{ aspects: {...}, positiveComments: [...], negativeComments: [...] }`
- [x] **字段名必须是 `sampleCount`**（不是 `mentionedCount`）——前端读的是这个
- [x] `aspects` 只含四个达标维度（剧情/演技/画面特效/配乐音效），"节奏""台词"覆盖率低于 5% 暂不展示
- [x] 每个维度含 `smoothedScore`、`sampleCount`、`sufficientData`、`rawScore`
- [x] `sufficientData=false`（样本量<10）时前端置灰
- [x] 代表句含 `text`、`confidence`、`triggerWords`
- [x] 雷达图能渲染，样本量正确显示
- [x] 测试：MockMvc 测 analyze 接口（字段名、四维、充分性标记、代表句结构）
