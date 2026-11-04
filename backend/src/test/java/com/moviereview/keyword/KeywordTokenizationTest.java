package com.moviereview.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键词提取的分词与 IDF 行为测试（P1 工单 04 + 05）。
 *
 * 测试接缝：{@link KeywordExtractor#extract(List, int)}。
 * 只断言输出（词、权重、数量），不断言内部分词器实现。
 */
class KeywordTokenizationTest {

    private final KeywordExtractor extractor = new TfIdfKeywordExtractor();

    private Set<String> words(List<Keyword> kws) {
        return kws.stream().map(Keyword::word).collect(Collectors.toSet());
    }

    // ========== 工单 04：分词质量 ==========

    @Test
    @DisplayName("分词 - 中文长句能被正确切开，不再产生未分词长串")
    void tokenize_splitsChineseSentence() {
        // 改造前：中文无空格 → 整句成为一个 token
        List<String> texts = List.of(
                "剧情紧凑特效炸裂演员演技在线",
                "剧情紧凑特效炸裂演员演技在线",
                "剧情紧凑特效炸裂演员演技在线"
        );
        Set<String> w = words(extractor.extract(texts, 10));

        assertTrue(w.contains("剧情") || w.contains("特效") || w.contains("演技"),
                "应切出中文实词，实际：" + w);
        // 不应出现跨词的超长串
        assertTrue(w.stream().noneMatch(s -> s.length() > 6),
                "不应出现超长未分词串，实际：" + w);
    }

    @Test
    @DisplayName("分词 - 英文功能词与纯数字被过滤（不再出现 the / and / 12）")
    void tokenize_filtersEnglishAndDigits() {
        List<String> texts = List.of(
                "the movie is good and 12 分",
                "the plot and 12 分",
                "and the 12"
        );
        Set<String> w = words(extractor.extract(texts, 20));

        assertFalse(w.contains("the"), "不应包含 the，实际：" + w);
        assertFalse(w.contains("and"), "不应包含 and，实际：" + w);
        assertTrue(w.stream().noneMatch(s -> s.matches("^\\d+$")),
                "不应包含纯数字，实际：" + w);
    }

    @Test
    @DisplayName("分词 - 输出只含纯中文词")
    void tokenize_onlyChineseTokens() {
        List<String> texts = List.of("剧情很棒，IMAX效果震撼，3D特别好");
        Set<String> w = words(extractor.extract(texts, 20));
        assertTrue(w.stream().allMatch(s -> s.matches("^[\\u4e00-\\u9fa5]+$")),
                "输出应只含纯中文，实际：" + w);
    }

    @Test
    @DisplayName("分词 - 完全离线可用（不触发网络，返回非空结果）")
    void tokenize_worksOffline() {
        List<String> texts = List.of("这部电影的剧情和演技都很好");
        List<Keyword> kws = extractor.extract(texts, 5);
        assertFalse(kws.isEmpty(), "离线也应产出关键词");
    }

    // ========== 工单 05：真 IDF ==========

    @Test
    @DisplayName("IDF - 全站通用词的权重被压低（低于只在本片出现的词）")
    void idf_suppressesGlobalTerms() {
        // 「电影」在每篇都出现（df 高），「壮丽」只在一篇出现（df 低）
        List<String> texts = List.of(
                "电影壮丽画面",
                "电影剧情",
                "电影配音",
                "电影表演",
                "电影节奏"
        );
        List<Keyword> kws = extractor.extract(texts, 10);

        double commonWeight = weightOf(kws, "电影");
        double rareWeight = weightOf(kws, "壮丽");

        assertTrue(rareWeight > commonWeight,
                "稀有词权重应高于全站通用词：壮丽=" + rareWeight + " 电影=" + commonWeight);
    }

    @Test
    @DisplayName("IDF - 同 TF 不同 df 时，df 低的词权重严格更高（隔离 TF 影响）")
    void idf_strictlyHigherForLowerDf() {
        // 关键：让两词的总词频(TF)完全相等，只让文档频率(df)不同。
        // 「普遍」在各 1 次出现在 2 篇 → TF=2, df=2
        // 「罕见」连续 2 次只出现在 1 篇 → TF=2, df=1
        List<String> texts = List.of(
                "普遍 罕见罕见",
                "普遍 其他"
        );
        List<Keyword> kws = extractor.extract(texts, 10);

        double common = weightOf(kws, "普遍");
        double rare = weightOf(kws, "罕见");

        assertTrue(common > 0 && rare > 0, "两个词都应出现，实际：" + kws);
        assertTrue(rare > common,
                "TF 相同时 df 低者权重应更高：罕见=" + rare + " 普遍=" + common);
    }

    @Test
    @DisplayName("IDF - 单文档语料不产生 NaN/Infinity")
    void idf_singleDocument_noNaN() {
        List<Keyword> kws = extractor.extract(List.of("剧情紧凑演技出色"), 5);
        assertFalse(kws.isEmpty());
        for (Keyword k : kws) {
            assertFalse(Double.isNaN(k.weight()), "权重不应为 NaN：" + k);
            assertFalse(Double.isInfinite(k.weight()), "权重不应为 Infinity：" + k);
            assertTrue(k.weight() >= 0, "权重不应为负：" + k);
        }
    }

    // ========== 既有契约不变 ==========

    @Test
    @DisplayName("契约 - topN 上限仍生效")
    void contract_topNRespected() {
        List<String> texts = List.of("剧情 演技 画面 音乐 节奏 台词 特效 导演 摄影 剪辑");
        assertTrue(extractor.extract(texts, 3).size() <= 3);
    }

    @Test
    @DisplayName("契约 - 空输入抛 IllegalArgumentException")
    void contract_emptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(List.of(), 5));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(null, 5));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(List.of("剧情"), 0));
    }

    @Test
    @DisplayName("契约 - 返回结构含 word/weight/count，且权重归一化到 [0,1]")
    void contract_shapeAndNormalization() {
        List<Keyword> kws = extractor.extract(List.of("剧情紧凑，演技出色，画面精美"), 5);
        assertFalse(kws.isEmpty());
        for (Keyword k : kws) {
            assertNotNull(k.word());
            assertTrue(k.weight() >= 0 && k.weight() <= 1.0,
                    "归一化权重应在 [0,1]：" + k);
            assertTrue(k.count() > 0, "count 应为正：" + k);
        }
        assertEquals(1.0, kws.get(0).weight(), 1e-9, "最高权重应归一化为 1.0");
    }

    private double weightOf(List<Keyword> kws, String word) {
        return kws.stream().filter(k -> k.word().equals(word))
                .mapToDouble(Keyword::weight).findFirst().orElse(0.0);
    }
}
