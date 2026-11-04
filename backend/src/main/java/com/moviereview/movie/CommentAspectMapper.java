package com.moviereview.movie;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentAspectMapper extends BaseMapper<CommentAspect> {

    /**
     * 批量插入（一条 SQL 多行 VALUES）。
     *
     * 纵表一条评论最多 6 行，4000 条评论约 24000 行，逐条插入同样拖慢重跑。
     *
     * @param list 待插入记录，非空
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<CommentAspect> list);
}
