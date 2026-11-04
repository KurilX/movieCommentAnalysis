package com.moviereview.demo;

import com.moviereview.sentiment.AnalysisResult;
import com.moviereview.sentiment.L0RuleSentimentAnalyzer;
import com.moviereview.sentiment.SentimentAnalyzer;
import com.moviereview.sentiment.SentimentPolarity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * L0 可达上限分析（P1 工单 02 的决策依据）。
 *
 * 回答一个问题：**在「无任何情感信号」的评论上，无论怎么扩词典都救不了**，
 * 那这类评论占多少？它们若默认判成某一类，Accuracy 的数学上限是多少？
 *
 * 这决定了「85% 验收线」是否现实中可达，避免盲目调参。
 */
public class L0CeilingAnalysis {

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "../data/comments.csv");
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);

        List<String> texts = new ArrayList<>();
        List<SentimentPolarity> gold = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            int lastComma = line.lastIndexOf(',');
            if (lastComma < 0) continue;
            String ratingStr = line.substring(lastComma + 1).trim();
            if (!ratingStr.matches("[1-5]")) continue;
            int rating = Integer.parseInt(ratingStr);
            String rest = line.substring(0, lastComma);
            int firstComma = rest.indexOf(',');
            int secondComma = rest.indexOf(',', firstComma + 1);
            if (secondComma < 0) continue;
            texts.add(rest.substring(firstComma + 1, secondComma).replace("\"", ""));
            gold.add(rating >= 4 ? SentimentPolarity.POSITIVE
                    : rating == 3 ? SentimentPolarity.NEUTRAL
                    : SentimentPolarity.NEGATIVE);
        }

        SentimentAnalyzer l0 = new L0RuleSentimentAnalyzer();

        int total = texts.size();
        int noSignal = 0;
        int[] noSignalGold = new int[3];   // pos, neu, neg
        int withSignal = 0;
        int[] withSignalGold = new int[3];

        for (int i = 0; i < total; i++) {
            AnalysisResult r = l0.analyze(texts.get(i));
            boolean hasSignal = !r.triggerWords().isEmpty();
            SentimentPolarity g = gold.get(i);
            int idx = g == SentimentPolarity.POSITIVE ? 0 : g == SentimentPolarity.NEUTRAL ? 1 : 2;
            if (hasSignal) { withSignal++; withSignalGold[idx]++; }
            else { noSignal++; noSignalGold[idx]++; }
        }

        System.out.printf("总评论: %d 条%n%n", total);

        System.out.printf("=== 有情感信号（词典命中）: %d 条 (%.1f%%) ===%n",
                withSignal, 100.0 * withSignal / total);
        System.out.printf("  金标准：正面 %d / 中性 %d / 负面 %d%n",
                withSignalGold[0], withSignalGold[1], withSignalGold[2]);

        System.out.printf("%n=== 无任何情感信号: %d 条 (%.1f%%) ===%n",
                noSignal, 100.0 * noSignal / total);
        System.out.printf("  金标准：正面 %d / 中性 %d / 负面 %d%n",
                noSignalGold[0], noSignalGold[1], noSignalGold[2]);
        System.out.printf("  → 这部分无论怎么扩词典都不会命中，只能靠「默认倾向」猜%n");

        // 上限推算：假设有信号的部分 100% 判对（完美），无信号部分全判某类
        System.out.println();
        System.out.println("=== Accuracy 数学上限（假设有信号部分全判对）===");
        for (SentimentPolarity fallback : SentimentPolarity.values()) {
            int noSignalCorrect = switch (fallback) {
                case POSITIVE -> noSignalGold[0];
                case NEUTRAL -> noSignalGold[1];
                case NEGATIVE -> noSignalGold[2];
            };
            double ceiling = (double) (withSignal + noSignalCorrect) / total;
            System.out.printf("  无信号默认判 %-8s → 上限 %.4f%n", fallback, ceiling);
        }

        System.out.println();
        System.out.println("=== 若「有信号部分」只能做到 85% 正确 ===");
        for (SentimentPolarity fallback : SentimentPolarity.values()) {
            int noSignalCorrect = switch (fallback) {
                case POSITIVE -> noSignalGold[0];
                case NEUTRAL -> noSignalGold[1];
                case NEGATIVE -> noSignalGold[2];
            };
            double est = (0.85 * withSignal + noSignalCorrect) / total;
            System.out.printf("  无信号默认判 %-8s → 约 %.4f%n", fallback, est);
        }
    }
}
