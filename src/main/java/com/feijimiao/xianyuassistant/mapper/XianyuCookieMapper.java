package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 闲鱼Cookie Mapper
 */
@Mapper
public interface XianyuCookieMapper extends BaseMapper<XianyuCookie> {

    /**
     * 根据账号ID查询最新Cookie
     */
    @Select("SELECT * FROM xianyu_cookie WHERE xianyu_account_id = #{accountId} ORDER BY created_time DESC LIMIT 1")
    XianyuCookie selectByAccountId(Long accountId);
}
