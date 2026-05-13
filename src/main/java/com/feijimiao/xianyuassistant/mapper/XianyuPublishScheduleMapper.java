package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuPublishSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuPublishScheduleMapper extends BaseMapper<XianyuPublishSchedule> {

    @Select("SELECT * FROM xianyu_publish_schedule " +
            "WHERE status = 0 AND scheduled_time <= strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours') " +
            "ORDER BY scheduled_time ASC, id ASC LIMIT #{limit}")
    List<XianyuPublishSchedule> selectDueTasks(@Param("limit") int limit);
}
