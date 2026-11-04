package com.moviereview.movie;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentAnalysisMapper extends BaseMapper<CommentAnalysis> {

    /**
     * 批量插入（一条 SQL 多行 VALUES）。
     *
     * 重跑全库时逐条 insert 会产生 N 次 SQL 往返，是 3 分钟耗时的主因。
     *
     * @param list 待插入记录，非空
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<CommentAnalysis> list);
}
