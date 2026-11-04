package com.moviereview.demo;

import com.moviereview.keyword.Keyword;
import com.moviereview.keyword.TfIdfKeywordExtractor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 关键词效果对比工具（P1 工单 04/05 的验收证据）。
 *
 * 对比同一批真实评论在两种实现下的词云输出：
 * - 旧实现（按空格硬切 + 只有 TF）
 * - 新实现（HanLP 分词 + TF×IDF）
 *
 * 结果写进 docs/项目状态审计.md 作为论文可视化素材。
 */
public class KeywordCompare {

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "../data/comments.csv");
        int topN = args.length > 1 ? Integer.parseInt(args[1]) : 15;

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);

        // 取第一部电影的全部评论（按 movieId 分组里的第一组）
        Map<String, List<String>> byMovie = new TreeMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] p = line.split(",", -1);
            if (p.length < 6) continue;
            String movieId = p[0];
            String text = String.join(",", Arrays.copyOfRange(p, 1, p.length - 4)).replace("\"", "").trim();
            if (text.isEmpty()) continue;
            byMovie.computeIfAbsent(movieId, k -> new ArrayList<>()).add(text);
        }

        String movieId = byMovie.keySet().iterator().next();
        List<String> texts = byMovie.get(movieId);
        System.out.printf("样本：电影 %s，共 %d 条评论%n%n", movieId, texts.size());

        System.out.println("=== 旧实现（空格硬切 + 仅 TF）===");
        for (Keyword k : oldExtract(texts, topN)) {
            System.out.printf("  %-12s %d%n", k.word(), k.count());
        }

        System.out.println();
        System.out.println("=== 新实现（HanLP 分词 + TF×IDF）===");
        TfIdfKeywordExtractor extractor = new TfIdfKeywordExtractor();
        for (Keyword k : extractor.extract(texts, topN)) {
            System.out.printf("  %-12s weight=%.3f count=%d%n", k.word(), k.weight(), k.count());
        }
    }

    /** 旧实现：把标点换空格后按空格切，统计词频。保留下来做对比基线。 */
    private static List<Keyword> oldExtract(List<String> texts, int topN) {
        java.util.Map<String, Integer> freq = new java.util.HashMap<>();
        for (String t : texts) {
            String cleaned = t.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ");
            for (String token : cleaned.split("\\s+")) {
                if (token.length() >= 2 && token.length() <= 6) {
                    freq.merge(token, 1, Integer::sum);
                }
            }
        }
        int max = freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new Keyword(e.getKey(), (double) e.getValue() / max, e.getValue()))
                .toList();
    }
}
