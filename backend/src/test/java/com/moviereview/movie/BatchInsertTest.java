package com.moviereview.movie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批量写入改造的行为测试（03 号工单）。
 *
 * 测试接缝：AnalysisService + 持久层的可观察结果（行数、内容、回滚）。
 * 只断言外部行为（库里有什么），不断言内部用了什么 SQL 或缓冲策略。
 *
 * 注意：种子数据量必须超过批量上限（500），否则批量路径根本不会被触发，
 * 测试就成了「用逐条插入也能过」的空壳。
 */
@SpringBootTest
@ActiveProfiles("test")
class BatchInsertTest {

    @Autowired private AnalysisService analysisService;
    @Autowired private MovieMapper movieMapper;
    @Autowired private CommentMapper commentMapper;
    @Autowired private CommentAnalysisMapper analysisMapper;
    @Autowired private CommentAspectMapper aspectMapper;

    private static final String MOVIE_ID = "Mbatch01";

    /** 略多于单批上限（500），确保至少发生一次批量冲刷 + 一次尾部冲刷。 */
    private static final int SEED_COUNT = 520;

    @BeforeEach
    void seed() {
        aspectMapper.delete(null);
        analysisMapper.delete(null);
        commentMapper.delete(null);
        movieMapper.delete(null);

        Movie m = new Movie();
        m.setId(MOVIE_ID);
        m.setTitle("批量测试片");
        m.setCommentCount(0);
        m.setCreatedAt(LocalDateTime.now());
        movieMapper.insert(m);

        for (int i = 0; i < SEED_COUNT; i++) {
            insertComment("剧情紧凑，演员演技在线，画面也很美 " + i);
        }
    }

    private CommentEntity insertComment(String text) {
        CommentEntity c = new CommentEntity();
        c.setMovieId(MOVIE_ID);
        c.setText(text);
        c.setAuthor("匿名");
        c.setRating(5);
        c.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        return c;
    }

    @Test
    @DisplayName("重跑后：分析结果行数 = 评论行数，方面结果覆盖六个维度")
    void rerunAll_writesAllRows() {
        int count = analysisService.rerunAll();

        long comments = commentMapper.selectCount(null);
        long analyses = analysisMapper.selectCount(null);
        long aspects = aspectMapper.selectCount(null);

        assertEquals(comments, count, "返回的条数应等于评论数");
        assertEquals(comments, analyses, "每条评论一条分析结果");
        assertEquals(0, aspects % comments, "方面结果行数应是评论数的整数倍");

        List<CommentAspect> rows = aspectMapper.selectList(null);
        long distinctAspects = rows.stream().map(CommentAspect::getAspectName).distinct().count();
        assertEquals(6, distinctAspects, "方面维度应为 6 个");
    }

    @Test
    @DisplayName("重跑两次：行数不变（覆盖而非追加）")
    void rerunAll_isIdempotent() {
        analysisService.rerunAll();
        long analysesAfterFirst = analysisMapper.selectCount(null);
        long aspectsAfterFirst = aspectMapper.selectCount(null);
        assertTrue(analysesAfterFirst > 0);

        analysisService.rerunAll();

        assertEquals(analysesAfterFirst, analysisMapper.selectCount(null),
                "重复重跑不应追加分析结果");
        assertEquals(aspectsAfterFirst, aspectMapper.selectCount(null),
                "重复重跑不应追加方面结果");
    }

    @Test
    @DisplayName("重跑中途失败：已冲刷的批次整体回滚，库中保持重跑前的数据")
    void rerunAll_rollsBackOnFailure() {
        // 先跑一次，制造一份「重跑前的旧数据」
        analysisService.rerunAll();
        long analysesBefore = analysisMapper.selectCount(null);
        long aspectsBefore = aspectMapper.selectCount(null);
        assertTrue(analysesBefore > 0);

        // 毒数据放在队尾：前面的评论足以让分析缓冲区至少冲刷一次，
        // 从而在抛异常时库里确实已经写入了新行 —— 没有回滚这些行就会残留。
        CommentEntity poison = insertComment("");
        long commentsWithPoison = commentMapper.selectCount(null);

        assertThrows(RuntimeException.class, () -> analysisService.rerunAll(),
                "遇到无法分析的评论时应抛异常");

        assertEquals(analysesBefore, analysisMapper.selectCount(null),
                "失败后分析结果应回滚到重跑前的行数，而不是被清空或残留半新数据");
        assertEquals(aspectsBefore, aspectMapper.selectCount(null),
                "失败后方面结果应回滚到重跑前的行数");
        assertEquals(commentsWithPoison, commentMapper.selectCount(null),
                "评论表不应被重跑影响");

        // 清理毒数据，避免影响其它测试
        commentMapper.deleteById(poison.getId());
    }
}
