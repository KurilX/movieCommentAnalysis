package com.moviereview.keyword;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HanLP 完整版语言模型的管理器。
 *
 * 设计要点（对应 spec 的决策）：
 * 1. **默认且演示路径永远走 portable**（jar 内置核心词典，完全离线）。
 *    本类只负责「可选增强」：把完整版数据包下载到本地目录，下次启动时自动启用。
 * 2. 数据包几百 MB，落在 gitignore 的目录里，不进 Git。
 * 3. **下载失败/中断绝不影响主流程**：状态只是标记为 FAILED，关键词提取继续用 portable。
 * 4. 启动时检测目录是否已有有效数据集，有则标记为就绪。
 *
 * 注意：HanLP portable 的 API 在检测到 `hanlp.properties` / 数据根目录时才会启用完整词典。
 * 本实现只做「数据包落盘 + 状态暴露 + 降级」，不改变 portable 的可用性。
 */
@Component
public class HanLpModelManager {

    private static final Logger log = LoggerFactory.getLogger(HanLpModelManager.class);

    /** 数据包官方下载地址（HanLP data 的 release 包）。 */
    private static final String DEFAULT_DATA_URL =
            "https://file.hankcs.com/hanlp/data/1.8.6/data-for-1.8.6.zip";

    /** 数据包落盘目录（gitignore）。 */
    private static final Path DATA_DIR = Path.of("hanlp-data");

    /** 数据包文件名。 */
    private static final Path DATA_ZIP = DATA_DIR.resolve("data.zip");

    /** 状态枚举。 */
    public enum State {
        /** 未下载：只有 portable 可用。 */
        NOT_DOWNLOADED,
        /** 下载中。 */
        DOWNLOADING,
        /** 已就绪：检测到有效数据包。 */
        READY,
        /** 下载失败：仍以 portable 运行。 */
        FAILED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.NOT_DOWNLOADED);
    private volatile String lastError;

    public HanLpModelManager() {
        detectExisting();
    }

    /** 启动时检测：目录里已有有效数据包则标记就绪，否则保持未下载。 */
    private void detectExisting() {
        if (Files.isDirectory(DATA_DIR) && hasValidData(DATA_DIR)) {
            state.set(State.READY);
            log.info("检测到 HanLP 完整版数据包，将使用完整版分词");
        } else {
            state.set(State.NOT_DOWNLOADED);
            log.info("未检测到 HanLP 完整版数据包，关键词提取使用 portable 分词（完全离线）");
        }
    }

    /** 目录里是否有非空的数据文件（zip 或解压后的目录）。 */
    private boolean hasValidData(Path dir) {
        try {
            if (Files.isRegularFile(DATA_ZIP) && Files.size(DATA_ZIP) > 1024 * 1024) {
                return true;
            }
            try (var stream = Files.list(dir)) {
                return stream.anyMatch(p -> Files.isDirectory(p) && !p.getFileName().toString().startsWith("."));
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** 当前状态。 */
    public State state() {
        return state.get();
    }

    /** 当前生效的分词器标识：portable 或 full。 */
    public String activeTokenizer() {
        return state.get() == State.READY ? "hanlp-full" : "hanlp-portable";
    }

    /** 最近一次错误信息（无错误时为 null）。 */
    public String lastError() {
        return lastError;
    }

    public boolean isDownloading() {
        return state.get() == State.DOWNLOADING;
    }

    /**
     * 触发完整版数据包下载（**异步**，立即返回）。
     *
     * 为什么必须异步：数据包几百 MB，同步下载会长时间占住一个 Tomcat 请求线程
     *（最多 30 分钟），并发几个请求就能把线程池耗干，直接拖垮演示。
     * 这里改为后台线程执行，接口立刻返回「下载中」，前端轮询状态。
     *
     * 下载失败只会把状态置为 FAILED 并记录错误，不抛出异常 —— 系统继续以 portable 工作。
     *
     * @return 是否成功发起（已在下载中或刚发起返回 true，无法发起返回 false）
     */
    public boolean downloadAsync() {
        if (!state.compareAndSet(State.NOT_DOWNLOADED, State.DOWNLOADING)
                && !state.compareAndSet(State.FAILED, State.DOWNLOADING)) {
            // 已在下载中，或已就绪 —— 不重复发起
            return false;
        }
        lastError = null;

        Thread worker = new Thread(this::downloadBlocking, "hanlp-model-download");
        worker.setDaemon(true);   // 守护线程：不阻止 JVM 退出
        worker.start();
        return true;
    }

    /**
     * 同步下载（供 {@link #downloadAsync()} 的后台线程调用，以及测试直接调用）。
     *
     * @return 是否成功
     */
    boolean downloadBlocking() {
        Path tmp = DATA_DIR.resolve("data.zip.part");
        try {
            Files.createDirectories(DATA_DIR);
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_DATA_URL))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                lastError = "HTTP " + response.statusCode();
                state.set(State.FAILED);
                log.warn("HanLP 完整版下载失败：{}，继续使用 portable", lastError);
                return false;
            }

            try (InputStream in = response.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tmp, DATA_ZIP, StandardCopyOption.REPLACE_EXISTING);
            state.set(State.READY);
            log.info("HanLP 完整版数据包下载完成：{}", DATA_ZIP.toAbsolutePath());
            return true;

        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            state.set(State.FAILED);
            log.warn("HanLP 完整版下载失败：{}，继续使用 portable", lastError);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 清理失败不影响主流程
            }
            return false;
        }
    }

    /** 供测试与诊断使用：数据目录。 */
    public Path dataDir() {
        return DATA_DIR;
    }
}
