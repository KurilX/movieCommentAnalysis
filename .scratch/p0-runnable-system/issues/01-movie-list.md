# 01: 电影列表能打开（首页）

**What to build:** 打开首页能看到 20 部真实电影的海报墙，每张卡片显示片名、年份、导演、评论数、好评率（来自真实星级）。支持按片名搜索、按好评率/评论数排序。

**Blocked by:** None (can start immediately)

**Status:** done

- [x] 生产建表脚本（`schema.sql`）包含 movie / comment / comment_analysis / comment_aspect / crawler_task / sys_user / user_favorite 七张表，启动时自动执行且幂等
- [x] `movie` 表有 20 部电影，`comment` 表有 4000 条评论（从 `data/*.csv` 导入）
- [x] 重复启动不产生重复数据
- [x] `GET /api/movies` 返回 `{ movies: [...] }`，字段：`id, title, year, director, posterUrl, positiveRate, commentCount`
- [x] `?q=` 能按片名过滤
- [x] `?sort=positiveRate|commentCount|title` 排序正确
- [x] 首页显示 20 张卡片，无报错
- [x] 测试：MockMvc 测列表接口（返回数量、排序、搜索、字段名）
