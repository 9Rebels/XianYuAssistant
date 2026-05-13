package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuNotificationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通知日志Mapper
 */
@Mapper
public interface XianyuNotificationLogMapper extends BaseMapper<XianyuNotificationLog> {

    @Select("SELECT * FROM xianyu_notification_log ORDER BY create_time DESC LIMIT 50")
    List<XianyuNotificationLog> selectLatest();
}
