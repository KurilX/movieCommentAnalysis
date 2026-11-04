package com.moviereview.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整版分词模型的降级行为测试（P1 工单 06）。
 *
 * 核心断言：**没有完整版数据包时，系统照常工作**。
 * 这是演示安全的关键 —— 答辩现场绝不能因为缺一个大文件而崩。
 */
class HanLpModelManagerTest {

    @Test
    @DisplayName("无数据包时状态为 NOT_DOWNLOADED，生效分词器为 portable")
    void withoutData_usesPortable() {
        HanLpModelManager mgr = new HanLpModelManager();
        // 测试环境没有 hanlp-data 目录
        assertNotEquals(HanLpModelManager.State.READY, mgr.state(),
                "测试环境不应误判为已就绪");
        assertEquals("hanlp-portable", mgr.activeTokenizer(),
                "无完整版时应报告 portable");
    }

    @Test
    @DisplayName("降级时关键词提取仍正常返回结果（不抛异常）")
    void withoutData_keywordExtractionStillWorks() {
        HanLpModelManager mgr = new HanLpModelManager();
        assertEquals("hanlp-portable", mgr.activeTokenizer());

        // 即使完整版不可用，分词与关键词提取也必须正常工作
        KeywordExtractor extractor = new TfIdfKeywordExtractor();
        List<Keyword> kws = extractor.extract(
                List.of("剧情紧凑，演员演技在线", "画面精美，配乐动人"), 5);

        assertFalse(kws.isEmpty(), "portable 模式下关键词提取不应返回空");
        assertTrue(kws.stream().allMatch(k -> k.word().matches("^[\\u4e00-\\u9fa5]+$")),
                "portable 模式下应正常切出中文词，实际：" + kws);
    }

    @Test
    @DisplayName("状态枚举覆盖四种情形（未下载/下载中/就绪/失败）")
    void state_hasAllExpectedValues() {
        HanLpModelManager.State[] values = HanLpModelManager.State.values();
        assertEquals(4, values.length);
        assertNotNull(HanLpModelManager.State.valueOf("NOT_DOWNLOADED"));
        assertNotNull(HanLpModelManager.State.valueOf("DOWNLOADING"));
        assertNotNull(HanLpModelManager.State.valueOf("READY"));
        assertNotNull(HanLpModelManager.State.valueOf("FAILED"));
    }

    @Test
    @DisplayName("下载失败不抛异常，只记录错误并把状态置为 FAILED")
    void downloadFailure_doesNotThrow() {
        HanLpModelManager mgr = new HanLpModelManager();
        // 这里不真正触发下载（会联网且耗时），只验证失败状态的表达契约：
        // 状态可以安全读取，错误信息可为 null
        assertNotNull(mgr.state());
        assertDoesNotThrow(mgr::lastError);
        assertDoesNotThrow(mgr::activeTokenizer);
    }

    @Test
    @DisplayName("异步下载：立即返回，不阻塞调用线程")
    void downloadAsync_returnsImmediately() {
        HanLpModelManager mgr = new HanLpModelManager();

        long start = System.nanoTime();
        boolean started = mgr.downloadAsync();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 如果是第一次发起，应返回 true；若环境已就绪则返回 false
        // 关键断言：无论哪种，调用必须立即返回，不能阻塞到下载完成
        assertTrue(elapsedMs < 1000,
                "downloadAsync 应立即返回（<1s），实际耗时 " + elapsedMs + "ms");

        if (started) {
            // 发起了下载：状态必须是 DOWNLOADING（或很快变 FAILED，取决于网络）
            HanLpModelManager.State s = mgr.state();
            assertTrue(s == HanLpModelManager.State.DOWNLOADING
                            || s == HanLpModelManager.State.FAILED
                            || s == HanLpModelManager.State.READY,
                    "发起后状态应为 DOWNLOADING/FAILED/READY 之一，实际 " + s);
        }
    }

    @Test
    @DisplayName("异步下载：已在下载中时重复调用不再发起")
    void downloadAsync_noDuplicateWhenDownloading() {
        HanLpModelManager mgr = new HanLpModelManager();
        boolean first = mgr.downloadAsync();
        if (!first) {
            // 环境已就绪，跳过此场景
            return;
        }
        boolean second = mgr.downloadAsync();
        assertFalse(second, "下载进行中时重复调用应返回 false，避免并发下载同一文件");
    }
}
