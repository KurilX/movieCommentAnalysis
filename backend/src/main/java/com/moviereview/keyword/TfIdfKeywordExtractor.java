package com.moviereview.keyword;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF 关键词提取器。
 *
 * 分词用 HanLP **portable**（纯 Java 单 jar、核心词典内置、完全离线）——
 * 演示路径绝不依赖实时网络是本项目的硬约束。
 *
 * 权重 = TF × IDF：
 * - 文档 = 单条评论，语料 = 传入的全部评论。
 * - IDF 让「电影」「觉得」这类全站通用词被自然压低，而不是仅靠停用词表硬挡。
 *
 * 为什么必须在分离词：中文没有空格，按空格切会把「剧情紧凑特效炸裂」当成
 * 一个 token，导致词云里出现「然而痛心的是」这类未分词长串。
 */
public class TfIdfKeywordExtractor implements KeywordExtractor {

    /** 停用词：挡掉单字虚词与无信息量高频词（通用词的抑制主要交给 IDF）。 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "些", "什么", "可以", "这个", "那个",
            "电影", "影片", "觉得", "感觉", "真的", "还", "但", "而", "又",
            "the", "and", "a", "an", "of", "to", "is", "it", "in", "for", "on", "with"
    );

    /** 只保留这些词性的词，滤掉标点(w)、数字(m)、量词(q)、拟声(o)等。 */
    private static final Set<String> KEEP_NATURES = Set.of(
            "n", "nr", "ns", "nt", "nz", "nnt", "nn", "ng", "nl",
            "v", "vd", "vn", "vg", "vi", "vl",
            "a", "ad", "an", "ag",
            "d", "z", "i", "l"
    );

    /** 词长范围：单字信息量太低，超长基本是专有名词串。 */
    private static final int MIN_LEN = 2;
    private static final int MAX_LEN = 6;

    @Override
    public List<Keyword> extract(List<String> texts, int topN) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("texts 不能为空");
        }
        if (topN <= 0) {
            throw new IllegalArgumentException("topN 必须为正");
        }

        // 第一步：分词 + 词频统计（TF）
        Map<String, Integer> termFreq = new HashMap<>();
        // 文档频率：每个词出现在多少条评论里（同一评论内只计一次）
        Map<String, Integer> docCounts = new HashMap<>();
        int documentCount = 0;

        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            documentCount++;

            Set<String> seenInDoc = new HashSet<>();
            for (String token : tokenize(text)) {
                termFreq.merge(token, 1, Integer::sum);
                seenInDoc.add(token);
            }
            for (String token : seenInDoc) {
                docCounts.merge(token, 1, Integer::sum);
            }
        }

        if (termFreq.isEmpty()) {
            return List.of();
        }

        // 第二步：IDF 与权重
        int totalDocs = Math.max(documentCount, 1);
        Map<String, Double> weights = new HashMap<>();
        for (Map.Entry<String, Integer> e : termFreq.entrySet()) {
            String token = e.getKey();
            int tf = e.getValue();
            int df = docCounts.getOrDefault(token, 1);
            double idf = Math.log((double) (totalDocs + 1) / (df + 1)) + 1.0;  // 平滑
            weights.put(token, tf * idf);
        }

        // 第三步：按权重降序取 topN
        double maxWeight = weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(topN)
                .map(e -> new Keyword(
                        e.getKey(),
                        maxWeight == 0 ? 0.0 : e.getValue() / maxWeight,
                        termFreq.getOrDefault(e.getKey(), 0)))
                .collect(Collectors.toList());
    }

    /**
     * 分词并过滤：只保留有意义的实词。
     *
     * 过滤掉：单字、超长串、停用词、纯数字、纯英文功能词、标点与符号。
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (Term term : HanLP.segment(text)) {
            String word = term.word.trim();
            if (word.length() < MIN_LEN || word.length() > MAX_LEN) continue;
            if (STOP_WORDS.contains(word)) continue;
            if (!word.matches("^[\\u4e00-\\u9fa5]+$")) continue;   // 只保留纯中文
            if (!KEEP_NATURES.contains(String.valueOf(term.nature))) continue;
            tokens.add(word);
        }
        return tokens;
    }

    @Override
    public String name() {
        return "tfidf";
    }
}
