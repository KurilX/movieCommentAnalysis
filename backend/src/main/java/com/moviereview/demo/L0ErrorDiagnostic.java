package com.moviereview.demo;

import com.moviereview.sentiment.L0RuleSentimentAnalyzer;
import com.moviereview.sentiment.SentimentAnalyzer;
import com.moviereview.sentiment.SentimentPolarity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * L0 误判诊断工具（P1 工单 01/02 的调参依据）。
 *
 * 作用：把「金标准说正面但引擎判成中性」的评论捞出来，看看到底缺什么词。
 * 没有这个工具，扩词典就是瞎猜。
 *
 * 用法：
 *   java -cp "..." com.moviereview.demo.L0ErrorDiagnostic [csv路径] [显示条数]
 */
public class L0ErrorDiagnostic {

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "../data/comments.csv");
        int show = args.length > 1 ? Integer.parseInt(args[1]) : 40;

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
            String text = rest.substring(firstComma + 1, secondComma).replace("\"", "");

            texts.add(text);
            gold.add(rating >= 4 ? SentimentPolarity.POSITIVE
                    : rating == 3 ? SentimentPolarity.NEUTRAL
                    : SentimentPolarity.NEGATIVE);
        }

        SentimentAnalyzer l0 = new L0RuleSentimentAnalyzer();

        // 分类统计误判
        int posToNeu = 0, posToNeg = 0, neuToPos = 0, neuToNeg = 0, negToPos = 0, negToNeu = 0;
        List<String> posToNeuSamples = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            SentimentPolarity g = gold.get(i);
            SentimentPolarity p = l0.analyze(texts.get(i)).polarity();
            if (g == p) continue;
            switch (g) {
                case POSITIVE -> {
                    if (p == SentimentPolarity.NEUTRAL) { posToNeu++; posToNeuSamples.add(texts.get(i)); }
                    else posToNeg++;
                }
                case NEUTRAL -> { if (p == SentimentPolarity.POSITIVE) neuToPos++; else neuToNeg++; }
                case NEGATIVE -> { if (p == SentimentPolarity.POSITIVE) negToPos++; else negToNeu++; }
            }
        }

        System.out.println("=== 误判构成 ===");
        System.out.printf("  真实正面 → 判中性 : %d 条  ← 最大头，扩词典要打这里%n", posToNeu);
        System.out.printf("  真实正面 → 判负面 : %d 条%n", posToNeg);
        System.out.printf("  真实中性 → 判正面 : %d 条%n", neuToPos);
        System.out.printf("  真实中性 → 判负面 : %d 条%n", neuToNeg);
        System.out.printf("  真实负面 → 判正面 : %d 条%n", negToPos);
        System.out.printf("  真实负面 → 判中性 : %d 条%n", negToNeu);

        System.out.println();
        System.out.printf("=== 「真实正面但判中性」样例（最短 %d 条，最短的通常最缺词）===%n", show);
        posToNeuSamples.stream()
                .sorted(Comparator.comparingInt(String::length))
                .limit(show)
                .forEach(s -> System.out.println("  · " + s));
    }
}
