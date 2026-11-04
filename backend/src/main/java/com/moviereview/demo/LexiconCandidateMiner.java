package com.moviereview.demo;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 情感词典候选词统计工具（P1 工单 01）。
 *
 * 作用：从真实影评语料中统计高频表达，作为扩充情感词典的候选来源。
 * 这是「选词过程可复现」的证据：答辩被问「你的词怎么来的」时，现场跑一遍即可。
 *
 * 用法（在 backend 目录下）：
 *   mvn -q exec:java -Dexec.mainClass=com.moviereview.demo.LexiconCandidateMiner
 * 或编译后：
 *   java -cp "target/classes;<hanlp jar>" com.moviereview.demo.LexiconCandidateMiner ../data/comments.csv 40
 *
 * 输出分两部分：
 * 1. 高频词候选（按出现次数降序，过滤单字虚词/标点/数字）
 * 2. 已被现有词典覆盖的词（用于确认哪些不必重复添加）
 *
 * 注意：本工具只做「统计」，极性判定由人工/开发期 AI 完成，运行时不参与。
 * 这正是 spec 的决策：判断发生在开发期，产物是静态词典文件，运行时纯规则、零网络。
 */
public class LexiconCandidateMiner {

    /** 停用词：出现频率极高但不携带情感信息的词。 */
    private static final Set<String> STOP = Set.of(
            "电影", "影片", "这部", "一部", "一个", "什么", "自己", "觉得", "感觉", "真的",
            "就是", "这个", "那个", "但是", "不过", "而且", "所以", "因为", "如果", "虽然",
            "可以", "没有", "还是", "这样", "那样", "时候", "东西", "地方", "问题", "事情",
            "我们", "他们", "你们", "大家", "观众", "导演", "演员", "故事", "片子", "作品",
            "一部电影", "整部", "整个", "非常", "特别", "比较", "一样", "一直", "已经",
            "很多", "一些", "这些", "那些", "之后", "之前", "然后", "现在", "最后", "开始"
    );

    /** 只保留这些词性（名词/动词/形容词/副词/习用语），滤掉标点(w)、数字(m)、量词(q)等。 */
    private static final Set<String> KEEP_NATURES = Set.of(
            "n", "nr", "ns", "nt", "nz", "nnt", "nn", "ng",
            "v", "vd", "vn", "vg", "vi",
            "a", "ad", "an", "ag",
            "d", "z", "i", "l"
    );

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "../data/comments.csv");
        int topN = args.length > 1 ? Integer.parseInt(args[1]) : 60;

        if (!Files.exists(csv)) {
            System.err.println("语料文件不存在: " + csv.toAbsolutePath());
            System.exit(1);
        }

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        Map<String, Integer> freq = new HashMap<>();
        int parsed = 0;

        for (int i = 1; i < lines.size(); i++) {
            String text = extractText(lines.get(i));
            if (text == null || text.isBlank()) continue;
            parsed++;

            for (Term t : HanLP.segment(text)) {
                String word = t.word.trim();
                if (word.length() < 2) continue;                 // 单字一律不要
                if (STOP.contains(word)) continue;
                if (!KEEP_NATURES.contains(String.valueOf(t.nature))) continue;
                if (!word.matches("^[\\u4e00-\\u9fa5]+$")) continue; // 只留纯中文
                freq.merge(word, 1, Integer::sum);
            }
        }

        System.out.printf("语料: %s%n", csv.toAbsolutePath());
        System.out.printf("解析评论: %d 条，去重候选词: %d 个%n%n", parsed, freq.size());

        System.out.printf("=== 高频候选词 Top %d（供开发期判定极性）===%n", topN);
        freq.entrySet().stream()
                .filter(e -> e.getValue() >= 3)     // 至少出现 3 次才值得收进词典
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(topN)
                .forEach(e -> System.out.printf("  %-8s %d%n", e.getKey(), e.getValue()));

        System.out.println();
        System.out.println("=== 下一步 ===");
        System.out.println("  把上面的词逐个读语境、判定极性(1/0/-1)、强度(1/2)、domain(film/general)，");
        System.out.println("  写入 sentiment-lexicon.json。运行时不再需要本工具。");
    }

    /**
     * 从 CSV 行里取 text 字段。
     *
     * 格式: movieId,text,author,publishTime,likeCount,rating
     * text 里可能含逗号（被引号包裹），因此不能简单 split。
     * 这里用「第 1 个逗号之后 到 倒数第 4 个逗号之前」定位。
     */
    private static String extractText(String line) {
        if (line == null || line.isBlank()) return null;

        // 引号包裹的字段按 CSV 规则处理
        if (line.contains("\"")) {
            int firstQuote = line.indexOf('"');
            int lastQuote = line.lastIndexOf('"');
            if (firstQuote >= 0 && lastQuote > firstQuote) {
                String inner = line.substring(firstQuote + 1, lastQuote);
                return inner.replace("\"\"", "\"").trim();
            }
        }

        // 无引号：movieId,text,author,,likeCount,rating
        // text 是第 2 列，后面固定还有 4 列
        String[] parts = line.split(",", -1);
        if (parts.length >= 6) {
            return String.join(",", Arrays.copyOfRange(parts, 1, parts.length - 4)).trim();
        }
        return null;
    }
}
