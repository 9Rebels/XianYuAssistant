package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAiProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface XianyuAiProviderMapper extends BaseMapper<XianyuAiProvider> {

    @Select("SELECT * FROM xianyu_ai_provider WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<XianyuAiProvider> findAllEnabled();

    @Select("SELECT * FROM xianyu_ai_provider WHERE is_active = 1 AND enabled = 1 LIMIT 1")
    XianyuAiProvider findActive();

    @Update("UPDATE xianyu_ai_provider SET is_active = 0 WHERE is_active = 1")
    void deactivateAll();
}
