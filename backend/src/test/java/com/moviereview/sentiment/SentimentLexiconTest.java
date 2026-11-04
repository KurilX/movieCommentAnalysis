package com.moviereview.sentiment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 情感词典加载与生效测试（P1 工单 01）。
 *
 * 测试接缝：{@link SentimentAnalyzer#analyze(String)} + {@link SentimentLexicon} 的加载契约。
 * 只测外部行为：给了词表之后引擎判什么；文件坏了会不会快速失败。
 */
class SentimentLexiconTest {

    private final SentimentAnalyzer analyzer = new L0RuleSentimentAnalyzer();

    // ========== 词典内容 ==========

    @Test
    @DisplayName("词典规模落在 300-500 目标区间")
    void lexicon_sizeInTargetRange() {
        int size = SentimentLexicon.loadDefault().size();
        assertTrue(size >= 300 && size <= 500,
                "词典规模应在 300-500，实际 " + size);
    }

    @Test
    @DisplayName("旧词典的 43 个词全部保留（极性不变）")
    void lexicon_keepsLegacyWords() {
        List<String> legacy = List.of(
                "好看", "精彩", "出色", "优秀", "感人", "震撼", "完美", "惊艳", "在线", "值得一看",
                "演技在线", "剧情紧凑", "画面精美", "配乐动人", "节奏流畅", "好",
                "美", "棒", "赞", "动人", "流畅", "紧凑",
                "难看", "糟糕", "拖沓", "无聊", "尴尬", "烂", "失望", "浮夸", "降智", "稀碎",
                "剧情拖沓", "演技尴尬", "画面粗糙", "配乐突兀", "节奏稀碎", "五毛特效", "面瘫",
                "还行", "一般", "凑合", "普通", "中规中矩", "过得去"
        );
        List<String> words = SentimentLexicon.loadDefault().entries().stream()
                .map(SentimentLexicon.Entry::word).toList();
        for (String w : legacy) {
            assertTrue(words.contains(w), "旧词条应保留：" + w);
        }
    }

    @Test
    @DisplayName("词条按词长降序排列（最长匹配优先）")
    void lexicon_sortedByLengthDesc() {
        var entries = SentimentLexicon.loadDefault().entries();
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i - 1).word().length() >= entries.get(i).word().length(),
                    "词条应按词长降序，实际在第 " + i + " 位乱序");
        }
    }

    // ========== 加载失败语义 ==========

    @Test
    @DisplayName("资源缺失时快速失败，且错误信息可读")
    void load_missingResource_failsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SentimentLexicon.load("no-such-lexicon.json"));
        assertTrue(e.getMessage().contains("不存在"),
                "错误信息应说明资源不存在，实际：" + e.getMessage());
    }

    @Test
    @DisplayName("JSON 非法时快速失败，不静默降级成空词典")
    void load_malformedJson_failsFast() {
        String bad = "{ this is not json";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> parse(bad));
        assertNotNull(e.getMessage());
    }

    @Test
    @DisplayName("词条缺字段时快速失败")
    void load_missingField_failsFast() {
        String bad = "{\"entries\":[{\"word\":\"好看\"}]}";
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> parse(bad));
        assertTrue(e.getMessage().contains("polarity"),
                "错误信息应指出缺哪个字段，实际：" + e.getMessage());
    }

    @Test
    @DisplayName("重复词条时快速失败")
    void load_duplicateWord_failsFast() {
        String bad = "{\"entries\":["
                + "{\"word\":\"好看\",\"polarity\":1,\"strength\":1,\"domain\":\"general\"},"
                + "{\"word\":\"好看\",\"polarity\":1,\"strength\":2,\"domain\":\"general\"}]}";
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> parse(bad));
        assertTrue(e.getMessage().contains("重复"), "应指出重复，实际：" + e.getMessage());
    }

    @Test
    @DisplayName("极性越界时快速失败")
    void load_invalidPolarity_failsFast() {
        String bad = "{\"entries\":[{\"word\":\"好看\",\"polarity\":5,\"strength\":1,\"domain\":\"general\"}]}";
        assertThrows(IllegalStateException.class, () -> parse(bad));
    }

    // ========== 对引擎生效 ==========

    @Test
    @DisplayName("影视领域新词生效：'封神' → 正面（证明读的是文件不是硬编码）")
    void analyze_filmDomainWord_positive() {
        AnalysisResult r = analyzer.analyze("这片真的封神了");
        assertEquals(SentimentPolarity.POSITIVE, r.polarity());
        assertTrue(r.triggerWords().contains("封神"), "触发词应包含封神，实际：" + r.triggerWords());
    }

    @Test
    @DisplayName("'值回票价' 等新词能被识别")
    void analyze_newPraiseWords_recognized() {
        assertEquals(SentimentPolarity.POSITIVE, analyzer.analyze("特效绝对值回票价").polarity());
        assertEquals(SentimentPolarity.POSITIVE, analyzer.analyze("剧情环环相扣,一气呵成").polarity());
        assertEquals(SentimentPolarity.NEGATIVE, analyzer.analyze("完全是烂片,浪费时间").polarity());
    }

    @Test
    @DisplayName("最长匹配：'演技在线' 不会被子串 '在线' 重复计数")
    void analyze_longestMatch_noDoubleCount() {
        AnalysisResult r = analyzer.analyze("演技在线");
        assertEquals(SentimentPolarity.POSITIVE, r.polarity());
        long onlineHits = r.triggerWords().stream().filter(w -> w.equals("在线")).count();
        assertEquals(0, onlineHits, "不应单独命中 '在线'，实际触发词：" + r.triggerWords());
        assertTrue(r.triggerWords().contains("演技在线"));
    }

    @Test
    @DisplayName("最长匹配：'好看' 不会被短词 '好' 抢先命中")
    void analyze_longestMatch_prefersLongerWord() {
        AnalysisResult r = analyzer.analyze("好看");
        assertTrue(r.triggerWords().contains("好看"),
                "应命中 '好看' 而非 '好'，实际：" + r.triggerWords());
    }

    @Test
    @DisplayName("词条内部的否定字不重复翻转：'不推荐' 本身已是负面")
    void analyze_negationInsideWord_noDoubleFlip() {
        // 老 bug：「不推荐」里的「不」被当独立否定词，把「推荐」的正面翻转成负面，
        // 而词条本身已经是负面，结果被算两次
        AnalysisResult r = analyzer.analyze("不推荐");
        assertEquals(SentimentPolarity.NEGATIVE, r.polarity());
        assertTrue(r.triggerWords().contains("不推荐"));
    }

    @Test
    @DisplayName("强度 2 的词权重更高：'神作' 置信度高于 '不错'")
    void analyze_strongWord_higherConfidence() {
        double strong = analyzer.analyze("神作").confidence();
        double mild = analyzer.analyze("不错").confidence();
        assertTrue(strong > mild,
                "强度 2 的词应产生更高置信度：神作=" + strong + " 不错=" + mild);
    }

    private SentimentLexicon parse(String json) throws IllegalStateException {
        try {
            return SentimentLexicon.fromJson(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }
}
