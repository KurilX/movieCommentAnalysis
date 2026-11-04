package com.moviereview.demo;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 候选词语境导出工具（P1 工单 01，配套 {@link LexiconCandidateMiner}）。
 *
 * 作用：给每个候选词抽出若干条真实语境，供开发期判定极性时「读语境」用，
 * 而不是凭词形瞎猜。例如「封神」只有看到「这片封神了」才能确定是强正面。
 *
 * 输出：候选词 → 3 条最短语境（最短通常最能体现该词的用法）。
 *
 * 用法（在 backend 目录下）：
 *   java -cp "target/classes;<hanlp jar>" com.moviereview.demo.LexiconContextDumper ../data/comments.csv 封神 炸裂 值回票价
 */
public class LexiconContextDumper {

    private static final int SAMPLES_PER_WORD = 3;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: LexiconContextDumper <csv路径> <词1> [词2 ...]");
            System.exit(1);
        }

        Path csv = Path.of(args[0]);
        List<String> words = Arrays.asList(args).subList(1, args.length);
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);

        List<String> texts = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String t = extractText(lines.get(i));
            if (t != null && !t.isBlank()) texts.add(t);
        }

        System.out.printf("语料: %d 条评论%n%n", texts.size());

        for (String w : words) {
            List<String> hits = new ArrayList<>();
            for (String t : texts) {
                if (t.contains(w)) hits.add(t);
            }

            System.out.printf("=== %s（命中 %d 条）===", w, hits.size());
            if (hits.isEmpty()) {
                System.out.println(" ← 语料中不存在，慎收");
                System.out.println();
                continue;
            }
            System.out.println();
            hits.stream()
                    .sorted(Comparator.comparingInt(String::length))
                    .limit(SAMPLES_PER_WORD)
                    .forEach(s -> System.out.println("  · " + s));
            System.out.println();
        }
    }

    private static String extractText(String line) {
        if (line == null || line.isBlank()) return null;
        if (line.contains("\"")) {
            int firstQuote = line.indexOf('"');
            int lastQuote = line.lastIndexOf('"');
            if (firstQuote >= 0 && lastQuote > firstQuote) {
                return line.substring(firstQuote + 1, lastQuote).replace("\"\"", "\"").trim();
            }
        }
        String[] parts = line.split(",", -1);
        if (parts.length >= 6) {
            return String.join(",", Arrays.copyOfRange(parts, 1, parts.length - 4)).trim();
        }
        return null;
    }
}
