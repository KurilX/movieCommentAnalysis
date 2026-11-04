# 02: 电影详情 + 情感分布饼图

**What to build:** 点进电影详情页，看到电影信息（片名/导演/简介/海报）和情感分布饼图（正面/中性/负面占比）。分析结果预计算落库，详情页秒开。

**Blocked by:** 01

**Status:** done

- [x] 预计算：对 4000 条评论跑 `SentimentAnalyzer`，结果写入 `comment_analysis` 表（含 polarity/confidence/trigger_words/analyzer_name/raw_json/elapsed_ms）
- [x] 预计算幂等：重复执行覆盖而非新增
- [x] `GET /api/movies/{id}` 返回 `{ movie: { id, title, director, actors, year, posterUrl, synopsis, sentimentDistribution: { positive, neutral, negative } } }`
- [x] `sentimentDistribution` 三项之和 = 该电影评论总数
- [x] 电影不存在时返回 404
- [x] 详情页显示电影信息 + 饼图，无报错
- [x] 测试：MockMvc 测详情接口（分布之和、404、字段名）
