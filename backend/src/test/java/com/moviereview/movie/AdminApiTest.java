package com.moviereview.movie;

import com.moviereview.user.UserRole;
import com.moviereview.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理后台接口测试（06 + 07 号工单）。
 *
 * 测试接缝：HTTP API 边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MovieMapper movieMapper;
    @Autowired private CommentMapper commentMapper;
    @Autowired private CommentAnalysisMapper analysisMapper;
    @Autowired private CommentAspectMapper aspectMapper;
    @Autowired private CrawlerTaskMapper taskMapper;
    @Autowired private UserService userService;
    @Autowired private com.moviereview.user.JwtUtil jwtUtil;

    private static final String M1 = "Madmin01";
    private String adminToken;

    @BeforeEach
    void seed() {
        taskMapper.delete(null);
        aspectMapper.delete(null);
        analysisMapper.delete(null);
        commentMapper.delete(null);
        movieMapper.delete(null);

        Movie m = new Movie();
        m.setId(M1);
        m.setTitle("后台测试片");
        m.setCommentCount(0);
        m.setCreatedAt(LocalDateTime.now());
        movieMapper.insert(m);

        for (int i = 0; i < 5; i++) {
            CommentEntity c = new CommentEntity();
            c.setMovieId(M1);
            c.setText("剧情紧凑好看");
            c.setAuthor("匿名");
            c.setRating(5);
            c.setCreatedAt(LocalDateTime.now());
            commentMapper.insert(c);
        }

        // 创建管理员并取 token
        try {
            var admin = userService.register("admin_test_" + System.nanoTime(), "password123", UserRole.ADMIN);
            adminToken = jwtUtil.generateToken(admin.getId(), admin.getUsername(), "ADMIN");
        } catch (Exception e) {
            adminToken = null;
        }
    }

    // ========== 06 号工单：爬虫任务 ==========

    @Test
    @DisplayName("POST /api/admin/crawler/trigger - 管理员可触发任务并落库")
    void triggerTask_createsRecord() throws Exception {
        mockMvc.perform(post("/api/admin/crawler/trigger")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"" + M1 + "\",\"maxCount\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").exists());

        assertEquals(1, taskMapper.selectCount(null), "任务应落库");
    }

    @Test
    @DisplayName("GET /api/admin/crawler/tasks - 返回任务列表")
    void listTasks_returnsList() throws Exception {
        mockMvc.perform(post("/api/admin/crawler/trigger")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"" + M1 + "\",\"maxCount\":50}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/crawler/tasks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].movieId").value(M1))
                .andExpect(jsonPath("$.tasks[0].status").exists())
                .andExpect(jsonPath("$.tasks[0].progress").exists());
    }

    @Test
    @DisplayName("POST trigger - 空 movieId 返回 400")
    void triggerTask_blankMovieId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/crawler/trigger")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\":\"\",\"maxCount\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("权限：普通用户访问爬虫接口返回 403")
    void crawler_asNormalUser_returns403() throws Exception {
        var user = userService.register("user_test_" + System.nanoTime(), "password123", UserRole.USER);
        String userToken = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");

        mockMvc.perform(get("/api/admin/crawler/tasks")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ========== 07 号工单：一键重跑 + 总览 ==========

    @Test
    @DisplayName("POST /api/admin/analysis/rerun-all - 重跑后分析行数 = 评论行数")
    void rerunAll_rowCountMatches() throws Exception {
        mockMvc.perform(post("/api/admin/analysis/rerun-all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());

        long comments = commentMapper.selectCount(null);
        long analyses = analysisMapper.selectCount(null);
        assertEquals(comments, analyses, "重跑后分析结果数应等于评论数");
    }

    @Test
    @DisplayName("POST rerun-all - 幂等：跑两次行数不变")
    void rerunAll_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/admin/analysis/rerun-all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        long afterFirst = analysisMapper.selectCount(null);

        mockMvc.perform(post("/api/admin/analysis/rerun-all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        long afterSecond = analysisMapper.selectCount(null);

        assertEquals(afterFirst, afterSecond, "重复重跑不应产生重复数据");
    }

    @Test
    @DisplayName("GET /api/admin/overview - 返回统计数据")
    void overview_returnsStats() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovies").exists())
                .andExpect(jsonPath("$.totalComments").exists())
                .andExpect(jsonPath("$.analyzedCount").exists())
                .andExpect(jsonPath("$.modelCount").exists());
    }

    @Test
    @DisplayName("权限：普通用户访问重跑接口返回 403")
    void rerun_asNormalUser_returns403() throws Exception {
        var user = userService.register("user2_test_" + System.nanoTime(), "password123", UserRole.USER);
        String userToken = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");

        mockMvc.perform(post("/api/admin/analysis/rerun-all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ========== 完整版分词模型（工单 06） ==========

    @Test
    @DisplayName("GET /api/admin/model/status - 管理员可查状态，含生效分词器")
    void modelStatus_returnsStateAndTokenizer() throws Exception {
        mockMvc.perform(get("/api/admin/model/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").exists())
                .andExpect(jsonPath("$.activeTokenizer").exists())
                .andExpect(jsonPath("$.downloading").exists());
    }

    @Test
    @DisplayName("GET model/status - 未下载完整版时生效分词器为 portable（降级可见）")
    void modelStatus_defaultsToPortable() throws Exception {
        mockMvc.perform(get("/api/admin/model/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTokenizer").value("hanlp-portable"));
    }

    @Test
    @DisplayName("权限：普通用户访问模型状态接口返回 403")
    void modelStatus_asNormalUser_returns403() throws Exception {
        var user = userService.register("user3_test_" + System.nanoTime(), "password123", UserRole.USER);
        String userToken = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");

        mockMvc.perform(get("/api/admin/model/status")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("权限：普通用户触发模型下载返回 403")
    void downloadModel_asNormalUser_returns403() throws Exception {
        var user = userService.register("user4_test_" + System.nanoTime(), "password123", UserRole.USER);
        String userToken = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");

        mockMvc.perform(post("/api/admin/model/download")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
