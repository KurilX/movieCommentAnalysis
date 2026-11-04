package com.moviereview.sentiment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L0 判定规则改动测试（P1 工单 02）。
 *
 * 覆盖三条规则：
 * a) 无信号时的默认倾向（NO_SIGNAL_FALLBACK）
 * b) 中性标记词与情感词共现时以情感词为准
 * c) 褒贬势均力敌时判中性（混合评价）
 *
 * 测试接缝：{@link SentimentAnalyzer#analyze(String)}，只断言外部行为。
 */
class L0RulesTest {

    private final SentimentAnalyzer analyzer = new L0RuleSentimentAnalyzer();

    // ========== 规则 b：中性标记词让位 ==========

    @Test
    @DisplayName("规则b - 仅中性标记词：'还行' → 中性")
    void neutralMarker_alone_returnsNeutral() {
        assertEquals(SentimentPolarity.NEUTRAL, analyzer.analyze("还行").polarity());
        assertEquals(SentimentPolarity.NEUTRAL, analyzer.analyze("一般").polarity());
        assertEquals(SentimentPolarity.NEUTRAL, analyzer.analyze("中规中矩").polarity());
    }

    @Test
    @DisplayName("规则b - 中性词 + 明确情感词：以情感词为准（'还行，但演技真好' → 正面）")
    void neutralMarker_withPositive_followsPositive() {
        AnalysisResult r = analyzer.analyze("还行，但演技真好");
        assertEquals(SentimentPolarity.POSITIVE, r.polarity(),
                "中性标记词不应压掉明确正面信号，实际触发词：" + r.triggerWords());
    }

    @Test
    @DisplayName("规则b - 中性词 + 明确负面词：以负面词为准")
    void neutralMarker_withNegative_followsNegative() {
        AnalysisResult r = analyzer.analyze("一般般吧，其实挺无聊的");
        assertEquals(SentimentPolarity.NEGATIVE, r.polarity(),
                "中性标记词不应压掉明确负面信号，实际触发词：" + r.triggerWords());
    }

    // ========== 规则 a：无信号兜底 ==========

    @Test
    @DisplayName("规则a - 完全无情感信号时按默认倾向判定（不再一律判中性）")
    void noSignal_usesFallback() {
        // 「擦」「Life」「★★★★☆」这类纯情绪片段/外语/评分符号，没有任何词典词
        SentimentPolarity p = analyzer.analyze("★★★★☆").polarity();
        assertNotEquals(SentimentPolarity.NEUTRAL, p,
                "无信号时不应一律判中性（原始 bug：62% 被误判为中性）");
    }

    @Test
    @DisplayName("规则a - 兜底判定的置信度明显低于有实证信号的情形")
    void noSignal_confidenceIsLow() {
        double fallback = analyzer.analyze("★★★★☆").confidence();
        double evidenced = analyzer.analyze("非常好看").confidence();
        assertTrue(fallback < evidenced,
                "兜底置信度(" + fallback + ")应低于有证据的(" + evidenced + ")");
        assertTrue(fallback <= 0.5, "兜底置信度不应高于 0.5，实际 " + fallback);
    }

    // ========== 规则 c：褒贬混合 ==========

    @Test
    @DisplayName("规则c - 褒贬势均力敌时判中性")
    void mixedBalanced_returnsNeutral() {
        // 「剧情紧凑」(正面) 与「配乐突兀」(负面) 强度相当
        AnalysisResult r = analyzer.analyze("剧情紧凑，但是配乐突兀");
        assertEquals(SentimentPolarity.NEUTRAL, r.polarity(),
                "褒贬各一条时应判中性，实际触发词：" + r.triggerWords());
    }

    @Test
    @DisplayName("规则c - 褒明显多于贬时仍按主导方向判（不因存在负面词就中和）")
    void mixedLopsided_followsDominant() {
        AnalysisResult r = analyzer.analyze("太好看了，非常经典，堪称完美，只是台词略生硬");
        assertEquals(SentimentPolarity.POSITIVE, r.polarity(),
                "正面信号明显占优时应判正面，实际触发词：" + r.triggerWords());
    }

    // ========== 回归：既有行为不变 ==========

    @Test
    @DisplayName("回归 - 局部否定仍生效：'这个电影不好' → 负面")
    void regression_localNegationStillWorks() {
        assertEquals(SentimentPolarity.NEGATIVE, analyzer.analyze("这个电影不好").polarity());
    }

    @Test
    @DisplayName("回归 - '不推荐，但是演技在线剧情紧凑' 不应被整体翻转为负面")
    void regression_doesNotFlipWholeComment() {
        assertNotEquals(SentimentPolarity.NEGATIVE,
                analyzer.analyze("不推荐，但是演技在线剧情紧凑").polarity());
    }

    @Test
    @DisplayName("回归 - 空/null 文本仍抛 IllegalArgumentException")
    void regression_blankThrows() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(null));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze("   "));
    }
}
