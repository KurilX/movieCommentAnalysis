package com.moviereview.movie;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviereview.aspect.AspectAnalyzer;
import com.moviereview.aspect.AspectResult;
import com.moviereview.aspect.RuleBasedAspectAnalyzer;
import com.moviereview.keyword.Keyword;
import com.moviereview.keyword.TfIdfKeywordExtractor;
import com.moviereview.sentiment.AnalysisResult;
import com.moviereview.sentiment.L0RuleSentimentAnalyzer;
import com.moviereview.sentiment.SentimentAnalyzer;
import com.moviereview.sentiment.SentimentPolarity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 分析服务：预计算 + 电影级聚合。
 *
 * 详情页读预计算结果（秒开），提交评论走实时分析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final CommentMapper commentMapper;
    private final CommentAnalysisMapper analysisMapper;
    private final CommentAspectMapper aspectMapper;
    private final ObjectMapper objectMapper;

    private final SentimentAnalyzer analyzer = new L0RuleSentimentAnalyzer();
    private final AspectAnalyzer aspectAnalyzer = new RuleBasedAspectAnalyzer(new L0RuleSentimentAnalyzer());
    private final TfIdfKeywordExtractor keywordExtractor = new TfIdfKeywordExtractor();

    /**
     * 雷达图展示的四个达标维度。
     *
     * 覆盖率实测（4000 条）：剧情 12.95%、演技 7.98%、画面特效 5.23%、配乐音效 5.43%；
     * "节奏" 2.20%、"台词" 1.33% 低于 5% 阈值，暂不展示（P1 重新设计词典）。
     */
    private static final List<String> DISPLAYED_ASPECTS =
            List.of("剧情", "演技", "画面特效", "配乐音效");

    public List<String> displayedAspects() {
        return DISPLAYED_ASPECTS;
    }

    // ========== 预计算 ==========

    /** 批量写入的单批行数上限。 */
    private static final int BATCH_SIZE = 500;

    /** 方面维度数（纵表一条评论最多产生这么多行）。 */
    private static final int ASPECT_COUNT = 6;

    /**
     * 重跑全库分析（幂等：覆盖而非新增）。
     *
     * 性能：4000 条评论 × (1 analysis + 6 aspect) ≈ 28000 行。
     * 逐条 insert 会产生 28000 次 SQL 往返（实测约 3 分钟），
     * 这里改为「多行 VALUES 批量插入 + 每 BATCH_SIZE 行冲刷一次」。
     *
     * 事务：整体单事务（首行即全表清空，必须保证「要么全旧要么全新」）。
     *
     * @return 分析的评论条数
     */
    @org.springframework.transaction.annotation.Transactional
    public int rerunAll() {
        analysisMapper.delete(null);
        aspectMapper.delete(null);

        List<CommentEntity> comments = commentMapper.selectList(null);

        List<CommentAnalysis> analysisBuf = new ArrayList<>(BATCH_SIZE);
        List<CommentAspect> aspectBuf = new ArrayList<>(BATCH_SIZE * ASPECT_COUNT);
        int done = 0;

        for (CommentEntity c : comments) {
            Computed computed = compute(c);
            analysisBuf.add(toAnalysisRow(computed));
            aspectBuf.addAll(toAspectRows(computed));

            // 分析结果与方面结果各自按批冲刷，避免任何一个缓冲区无限增长
            if (analysisBuf.size() >= BATCH_SIZE) {
                analysisMapper.insertBatch(analysisBuf);
                analysisBuf.clear();
            }
            if (aspectBuf.size() >= BATCH_SIZE * ASPECT_COUNT) {
                aspectMapper.insertBatch(aspectBuf);
                aspectBuf.clear();
            }

            if (++done % 500 == 0) {
                log.info("预计算进度：{}/{}", done, comments.size());
            }
        }

        // 冲刷尾部不满一批的剩余行
        if (!analysisBuf.isEmpty()) {
            analysisMapper.insertBatch(analysisBuf);
        }
        if (!aspectBuf.isEmpty()) {
            aspectMapper.insertBatch(aspectBuf);
        }

        log.info("全库重跑完成：{} 条评论", comments.size());
        return comments.size();
    }

    /** 单条评论的分析产物（纯内存，尚未落库）。 */
    private record Computed(long commentId, AnalysisResult result, long elapsedMs,
                            Map<String, AspectResult> aspects) {}

    /** 跑分析器，纯内存计算，不碰数据库。 */
    private Computed compute(CommentEntity comment) {
        long start = System.currentTimeMillis();
        AnalysisResult result = analyzer.analyze(comment.getText());
        long elapsed = System.currentTimeMillis() - start;
        return new Computed(comment.getId(), result, elapsed,
                aspectAnalyzer.analyze(comment.getText()));
    }

    private CommentAnalysis toAnalysisRow(Computed computed) {
        CommentAnalysis ca = new CommentAnalysis();
        ca.setCommentId(computed.commentId());
        ca.setPolarity(computed.result().polarity().name());
        ca.setConfidence(computed.result().confidence());
        ca.setTriggerWords(toJson(computed.result().triggerWords()));
        ca.setAnalyzerName(analyzer.name());
        ca.setRawJson(computed.result().rawJson());
        ca.setElapsedMs(computed.elapsedMs());
        ca.setCreatedAt(LocalDateTime.now());
        return ca;
    }

    /** 把一条评论的方面结果摊平成纵表行。 */
    private List<CommentAspect> toAspectRows(Computed computed) {
        List<CommentAspect> rows = new ArrayList<>(computed.aspects().size());
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, AspectResult> e : computed.aspects().entrySet()) {
            AspectResult ar = e.getValue();
            CommentAspect cas = new CommentAspect();
            cas.setCommentId(computed.commentId());
            cas.setAspectName(e.getKey());
            cas.setMentioned(ar.mentioned());
            cas.setPolarity(ar.polarity().name());
            cas.setConfidence(ar.confidence());
            cas.setSampleCount(ar.sampleCount());
            cas.setTriggerWords(toJson(ar.triggerWords()));
            cas.setCreatedAt(now);
            rows.add(cas);
        }
        return rows;
    }

    /** 对一条评论做分析并落库（覆盖已有结果）。单条路径，不做批量。 */
    public AnalysisResult analyzeAndSave(CommentEntity comment) {
        // 清掉该评论的旧结果（保证幂等）
        analysisMapper.delete(new LambdaQueryWrapper<CommentAnalysis>()
                .eq(CommentAnalysis::getCommentId, comment.getId()));
        aspectMapper.delete(new LambdaQueryWrapper<CommentAspect>()
                .eq(CommentAspect::getCommentId, comment.getId()));

        Computed computed = compute(comment);
        analysisMapper.insert(toAnalysisRow(computed));
        for (CommentAspect cas : toAspectRows(computed)) {
            aspectMapper.insert(cas);
        }
        return computed.result();
    }

    // ========== 电影级聚合（详情页用） ==========

    /**
     * 方面聚合（雷达图数据）。
     *
     * 字段名用 sampleCount（前端读取的字段名），不是 mentionedCount。
     */
    public Map<String, Map<String, Object>> movieAspects(String movieId) {
        List<Long> commentIds = commentIdsOf(movieId);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String aspect : DISPLAYED_ASPECTS) {
            result.put(aspect, emptyAspect(aspect));
        }
        if (commentIds.isEmpty()) {
            return result;
        }

        List<CommentAspect> rows = aspectMapper.selectList(
                new LambdaQueryWrapper<CommentAspect>().in(CommentAspect::getCommentId, commentIds));

        Map<String, int[]> counters = new LinkedHashMap<>(); // [pos, neu, neg, total]
        for (String a : DISPLAYED_ASPECTS) counters.put(a, new int[4]);

        for (CommentAspect row : rows) {
            if (!Boolean.TRUE.equals(row.getMentioned())) continue;
            int[] c = counters.get(row.getAspectName());
            if (c == null) continue;
            switch (row.getPolarity() == null ? "NEUTRAL" : row.getPolarity()) {
                case "POSITIVE" -> c[0]++;
                case "NEGATIVE" -> c[2]++;
                default -> c[1]++;
            }
            c[3]++;
        }

        for (Map.Entry<String, int[]> e : counters.entrySet()) {
            int[] c = e.getValue();
            int pos = c[0], neu = c[1], neg = c[2], total = c[3];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("aspectName", e.getKey());
            m.put("sampleCount", total);
            m.put("positiveCount", pos);
            m.put("neutralCount", neu);
            m.put("negativeCount", neg);
            m.put("rawScore", rawScore(pos, total));
            m.put("smoothedScore", smoothedScore(pos, total));
            m.put("sufficientData", total >= 10);
            m.put("polarity", total == 0 ? "NEUTRAL" : (pos >= neg ? "POSITIVE" : "NEGATIVE"));
            result.put(e.getKey(), m);
        }
        return result;
    }

    private Map<String, Object> emptyAspect(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aspectName", name);
        m.put("sampleCount", 0);
        m.put("positiveCount", 0);
        m.put("neutralCount", 0);
        m.put("negativeCount", 0);
        m.put("rawScore", 0.0);
        m.put("smoothedScore", 50.0);
        m.put("sufficientData", false);
        m.put("polarity", "NEUTRAL");
        return m;
    }

    /** 正面占比（0-100）。复用 AspectAggregate 的实现，避免公式重复。 */
    static double rawScore(int positive, int total) {
        return com.moviereview.aspect.AspectAggregate.rawScore(positive, total);
    }

    /** 贝叶斯平滑（小样本向 50 分收缩）。复用 AspectAggregate 的实现。 */
    static double smoothedScore(int positive, int total) {
        return com.moviereview.aspect.AspectAggregate.smoothedScore(positive, total);
    }

    /**
     * 好评/差评代表句（带置信度和触发词）。
     */
    public List<Map<String, Object>> representativeComments(String movieId, String polarity, int limit) {
        List<Long> commentIds = commentIdsOf(movieId);
        if (commentIds.isEmpty()) return List.of();

        List<CommentAnalysis> analyses = analysisMapper.selectList(
                new LambdaQueryWrapper<CommentAnalysis>()
                        .in(CommentAnalysis::getCommentId, commentIds)
                        .eq(CommentAnalysis::getPolarity, polarity)
                        .orderByDesc(CommentAnalysis::getConfidence)
                        .last("LIMIT " + limit));
        if (analyses.isEmpty()) return List.of();

        Map<Long, String> textById = new HashMap<>();
        for (CommentEntity c : commentMapper.selectBatchIds(
                analyses.stream().map(CommentAnalysis::getCommentId).toList())) {
            textById.put(c.getId(), c.getText());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (CommentAnalysis a : analyses) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", textById.getOrDefault(a.getCommentId(), ""));
            m.put("confidence", a.getConfidence());
            m.put("triggerWords", fromJsonList(a.getTriggerWords()));
            out.add(m);
        }
        return out;
    }

    /**
     * 关键词（词云数据）。
     */
    public List<Map<String, Object>> keywords(String movieId, int topN) {
        List<String> texts = new ArrayList<>();
        for (CommentEntity c : commentMapper.selectList(
                new LambdaQueryWrapper<CommentEntity>().eq(CommentEntity::getMovieId, movieId))) {
            texts.add(c.getText());
        }
        if (texts.isEmpty()) return List.of();

        List<Keyword> kws = keywordExtractor.extract(texts, topN);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Keyword k : kws) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("word", k.word());
            m.put("weight", k.weight());
            m.put("count", k.count());
            out.add(m);
        }
        return out;
    }

    public long countAnalyzed() {
        return analysisMapper.selectCount(null);
    }

    private List<Long> commentIdsOf(String movieId) {
        return commentMapper.selectList(
                        new LambdaQueryWrapper<CommentEntity>().eq(CommentEntity::getMovieId, movieId))
                .stream().map(CommentEntity::getId).toList();
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
