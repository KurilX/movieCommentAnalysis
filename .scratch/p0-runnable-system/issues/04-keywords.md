# 04: 关键词词云

**What to build:** 详情页显示关键词标签云，按权重分级显示大小和颜色。

**Blocked by:** 02

**Status:** done

- [x] `GET /api/movies/{id}/keywords` 返回 `{ keywords: [{ word, weight, count }] }`
- [x] 按权重降序，取前 30 个
- [x] 电影不存在或无评论时返回空列表（不报错）
- [x] 详情页词云能渲染
- [x] 测试：MockMvc 测关键词接口（非空、降序、字段名）
