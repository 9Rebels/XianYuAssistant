package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuItemPolishTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuItemPolishTaskMapper extends BaseMapper<XianyuItemPolishTask> {

    @Select("SELECT * FROM xianyu_item_polish_task WHERE xianyu_account_id = #{accountId} LIMIT 1")
    XianyuItemPolishTask selectByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT * FROM xianyu_item_polish_task " +
            "WHERE enabled = 1 AND next_run_time IS NOT NULL AND next_run_time <= datetime('now', 'localtime') " +
            "ORDER BY next_run_time ASC LIMIT #{limit}")
    List<XianyuItemPolishTask> selectDueTasks(@Param("limit") int limit);
}
