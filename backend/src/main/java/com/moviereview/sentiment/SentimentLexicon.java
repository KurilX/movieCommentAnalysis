package com.moviereview.sentiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 情感词典加载器。
 *
 * 词表存在 JSON 资源文件里（不硬编码在 Java 代码中），400 词的规模才可维护、
 * diff 可读、可直接引用进论文附录。
 *
 * 加载策略：
 * - 启动时一次性读入内存，建成「按词长降序」的有序列表，供最长匹配使用。
 * - **失败即抛出异常**，绝不静默降级成空词典 —— 空词典会让全站变成「全判中性」，
 *   比直接崩溃更难发现。
 *
 * 字段约定：polarity 1=正面 / 0=中性 / -1=负面；strength 1=普通 / 2=强烈；
 * domain film=影视领域词 / general=通用情感词。
 */
public final class SentimentLexicon {

    /** 词条。 */
    public record Entry(String word, int polarity, int strength, String domain) {
        public boolean isPositive() { return polarity > 0; }
        public boolean isNegative() { return polarity < 0; }
        public boolean isNeutral() { return polarity == 0; }
    }

    private final List<Entry> entries;

    private SentimentLexicon(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * 从 classpath 默认位置加载。
     *
     * @throws IllegalStateException 资源缺失或内容非法
     */
    public static SentimentLexicon loadDefault() {
        return load("sentiment-lexicon.json");
    }

    /**
     * 从 classpath 指定位置加载。
     *
     * @param resourcePath 资源路径
     * @throws IllegalStateException 资源缺失、JSON 非法、词条缺字段
     */
    public static SentimentLexicon load(String resourcePath) {
        try (InputStream in = SentimentLexicon.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "情感词典资源不存在: " + resourcePath
                                + "（应位于 src/main/resources 下；缺失会导致全站判为中性）");
            }
            return fromJson(in, resourcePath);
        } catch (IOException e) {
            throw new IllegalStateException("读取情感词典失败: " + resourcePath, e);
        }
    }

    /** 从输入流解析。 */
    static SentimentLexicon fromJson(InputStream in, String source) throws IOException {
        JsonNode root = new ObjectMapper().readTree(in);
        JsonNode arr = root.path("entries");
        if (!arr.isArray() || arr.isEmpty()) {
            throw new IllegalStateException("情感词典条目不合法（entries 缺失或为空）: " + source);
        }

        List<Entry> parsed = new ArrayList<>(arr.size());
        Set<String> seen = new HashSet<>();
        for (JsonNode n : arr) {
            String word = n.path("word").asText("").trim();
            if (word.isEmpty()) {
                throw new IllegalStateException("情感词典条缺少 word 字段: " + n + "（来源 " + source + "）");
            }
            if (!n.has("polarity") || !n.has("strength") || !n.has("domain")) {
                throw new IllegalStateException(
                        "情感词典条缺少 polarity/strength/domain 字段: " + word + "（来源 " + source + "）");
            }
            int polarity = n.get("polarity").asInt();
            int strength = n.get("strength").asInt();
            String domain = n.get("domain").asText("");

            if (polarity < -1 || polarity > 1) {
                throw new IllegalStateException("词条 " + word + " 的 polarity 必须是 -1/0/1，实际 " + polarity);
            }
            if (strength < 1 || strength > 2) {
                throw new IllegalStateException("词条 " + word + " 的 strength 必须是 1/2，实际 " + strength);
            }
            if (!seen.add(word)) {
                throw new IllegalStateException("情感词典存在重复词条: " + word);
            }
            parsed.add(new Entry(word, polarity, strength, domain));
        }

        // 按词长降序：最长匹配优先，避免「好」吃掉「好看」、「烂」吃掉「烂片」
        parsed.sort(Comparator
                .comparingInt((Entry e) -> e.word().length()).reversed()
                .thenComparing(Entry::word));

        return new SentimentLexicon(parsed);
    }

    /** 全部词条（已按词长降序）。 */
    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /** 正面词条。 */
    public List<Entry> positives() {
        return entries.stream().filter(Entry::isPositive).toList();
    }

    /** 负面词条。 */
    public List<Entry> negatives() {
        return entries.stream().filter(Entry::isNegative).toList();
    }

    /** 中性标记词条。 */
    public List<Entry> neutrals() {
        return entries.stream().filter(Entry::isNeutral).toList();
    }
}
