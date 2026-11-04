package com.moviereview.demo;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.moviereview.sentiment.SentimentLexicon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 数据驱动的候选词筛选（P1 工单 01/02）。
 *
 * 比 {@link LexiconCandidateMiner} 更进一步：不只统计词频，而是计算每个候选词在
 * 「正面金标准评论」与「负面金标准评论」中的**出现率对比**，
 * 给出一个极性倾向分，让扩词典从「凭感觉」变成「按数据」。
 *
 * 评分：
 *   posRate = 出现在正面评论的次数 / 正面评论总数
 *   negRate = 出现在负面评论的次数 / 负面评论总数
 *   score   = posRate - negRate   （正值=偏正面，负值=偏负面）
 *
 * 只看那些「明显偏一侧」且「语料中不存在于现有词典」的词，避免重复收录。
 *
 * 用法：
 *   java -cp "..." com.moviereview.demo.LexiconPolarityScorer [csv] [最少出现次数] [显示条数]
 */
public class LexiconPolarityScorer {

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "../data/comments.csv");
        int minCount = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int show = args.length > 2 ? Integer.parseInt(args[2]) : 80;

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<String> texts = new ArrayList<>();
        List<Integer> ratings = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] p = line.split(",", -1);
            if (p.length < 6) continue;
            String ratingStr = p[p.length - 1].trim();
            if (!ratingStr.matches("[1-5]")) continue;
            texts.add(String.join(",", Arrays.copyOfRange(p, 1, p.length - 4)).replace("\"", "").trim());
            ratings.add(Integer.parseInt(ratingStr));
        }

        int posTotal = 0, negTotal = 0;
        for (int r : ratings) {
            if (r >= 4) posTotal++;
            else if (r <= 2) negTotal++;
        }

        // word -> [posCommentsContaining, negCommentsContaining]
        Map<String, int[]> stat = new HashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            int r = ratings.get(i);
            boolean isPos = r >= 4, isNeg = r <= 2;
            if (!isPos && !isNeg) continue;   // 中性评论不参与极性倾向计算

            Set<String> seen = new HashSet<>();
            for (Term t : HanLP.segment(texts.get(i))) {
                String w = t.word.trim();
                if (w.length() < 2 || w.length() > 5) continue;
                if (!w.matches("^[\\u4e00-\\u9fa5]+$")) continue;
                if (!seen.add(w)) continue;
                int[] c = stat.computeIfAbsent(w, k -> new int[2]);
                if (isPos) c[0]++;
                else c[1]++;
            }
        }

        SentimentLexicon lexicon = SentimentLexicon.loadDefault();
        Set<String> known = new HashSet<>();
        for (SentimentLexicon.Entry e : lexicon.entries()) known.add(e.word());

        record Cand(String word, int pos, int neg, double score, double totalFreq) {}
        List<Cand> cands = new ArrayList<>();
        for (var e : stat.entrySet()) {
            String w = e.getKey();
            if (known.contains(w)) continue;
            int pc = e.getValue()[0], nc = e.getValue()[1];
            int total = pc + nc;
            if (total < minCount) continue;
            double posRate = posTotal == 0 ? 0 : (double) pc / posTotal;
            double negRate = negTotal == 0 ? 0 : (double) nc / negTotal;
            cands.add(new Cand(w, pc, nc, posRate - negRate, total));
        }

        System.out.printf("正面评论 %d 条 / 负面评论 %d 条（中性评论不参与）%n%n", posTotal, negTotal);

        System.out.printf("=== 偏正面候选（尚未收录）Top %d ===%n", show);
        System.out.println("  词        正/负  倾向分");
        cands.stream().filter(c -> c.score() > 0)
                .sorted(Comparator.comparingDouble(Cand::score).reversed()
                        .thenComparing(Comparator.comparingDouble(Cand::totalFreq).reversed()))
                .limit(show)
                .forEach(c -> System.out.printf("  %-8s %3d/%-3d %+.4f%n", c.word(), c.pos(), c.neg(), c.score()));

        System.out.println();
        System.out.printf("=== 偏负面候选（尚未收录）Top %d ===%n", show / 2);
        System.out.println("  词        正/负  倾向分");
        cands.stream().filter(c -> c.score() < 0)
                .sorted(Comparator.comparingDouble(Cand::score)
                        .thenComparing(Comparator.comparingDouble(Cand::totalFreq).reversed()))
                .limit(show / 2)
                .forEach(c -> System.out.printf("  %-8s %3d/%-3d %+.4f%n", c.word(), c.pos(), c.neg(), c.score()));
    }
}
