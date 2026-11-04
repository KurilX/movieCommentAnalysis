package com.moviereview.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台接口（06 + 07 号工单）。
 *
 * 路径与前端 api.js 严格对齐，受 SecurityConfig 的 /api/admin/** 保护（需 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CrawlerTaskService crawlerTaskService;
    private final AnalysisService analysisService;
    private final MovieService movieService;
    private final com.moviereview.keyword.HanLpModelManager modelManager;

    // ========== 06 号工单：爬虫任务 ==========

    @GetMapping("/crawler/tasks")
    public ResponseEntity<?> tasks() {
        return ResponseEntity.ok(Map.of("tasks", crawlerTaskService.list()));
    }

    @PostMapping("/crawler/trigger")
    public ResponseEntity<?> trigger(@RequestBody Map<String, Object> body) {
        try {
            String movieId = body.get("movieId") == null ? null : String.valueOf(body.get("movieId"));
            int maxCount = body.get("maxCount") == null ? 100
                    : Integer.parseInt(String.valueOf(body.get("maxCount")));
            Long taskId = crawlerTaskService.trigger(movieId, maxCount);
            return ResponseEntity.ok(Map.of("taskId", taskId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== 07 号工单：一键重跑 + 数据总览 ==========

    @PostMapping("/analysis/rerun-all")
    public ResponseEntity<?> rerunAll() {
        int count = analysisService.rerunAll();
        return ResponseEntity.ok(Map.of(
                "message", "重跑完成",
                "count", count
        ));
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalMovies", movieService.countMovies());
        m.put("totalComments", movieService.countComments());
        m.put("analyzedCount", analysisService.countAnalyzed());
        m.put("modelCount", 1);
        return ResponseEntity.ok(m);
    }

    // ========== 完整版分词模型（可选增强，演示永远走 portable） ==========

    /**
     * 查询完整版语言模型状态。
     *
     * 前端据此显示「未下载 / 下载中 / 已就绪 / 失败」与当前生效的分词器。
     */
    @GetMapping("/model/status")
    public ResponseEntity<?> modelStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", modelManager.state().name());
        m.put("activeTokenizer", modelManager.activeTokenizer());
        m.put("downloading", modelManager.isDownloading());
        m.put("error", modelManager.lastError());
        return ResponseEntity.ok(m);
    }

    /**
     * 触发完整版语言模型下载。
     *
     * **异步**：数据包几百 MB，同步下载会占住请求线程最多 30 分钟、拖垮线程池。
     * 这里立即返回「已发起」，前端轮询 /model/status 看进度。
     * 下载失败也不阻断任何功能，系统继续用 portable。
     */
    @PostMapping("/model/download")
    public ResponseEntity<?> downloadModel() {
        boolean started = modelManager.downloadAsync();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started", started);
        m.put("state", modelManager.state().name());
        m.put("activeTokenizer", modelManager.activeTokenizer());
        m.put("error", modelManager.lastError());
        m.put("message", started
                ? "已开始下载，请稍后刷新状态查看进度"
                : "当前状态不允许重复下载（" + modelManager.state().name() + "）");
        return ResponseEntity.ok(m);
    }
}
