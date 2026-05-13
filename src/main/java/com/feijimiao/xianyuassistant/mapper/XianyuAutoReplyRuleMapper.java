package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAutoReplyRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 自动回复规则Mapper
 */
@Mapper
public interface XianyuAutoReplyRuleMapper extends BaseMapper<XianyuAutoReplyRule> {

    @Select("SELECT * FROM xianyu_auto_reply_rule " +
            "WHERE xianyu_account_id = #{accountId} " +
            "AND enabled = 1 AND COALESCE(is_default, 0) = #{isDefault} " +
            "AND xy_goods_id = #{xyGoodsId} " +
            "ORDER BY priority ASC, id DESC")
    List<XianyuAutoReplyRule> selectGoodsRules(@Param("accountId") Long accountId,
                                               @Param("xyGoodsId") String xyGoodsId,
                                               @Param("isDefault") Integer isDefault);

    @Select("SELECT * FROM xianyu_auto_reply_rule " +
            "WHERE xianyu_account_id = #{accountId} " +
            "AND enabled = 1 AND COALESCE(is_default, 0) = #{isDefault} " +
            "AND (xy_goods_id IS NULL OR TRIM(xy_goods_id) = '') " +
            "ORDER BY priority ASC, id DESC")
    List<XianyuAutoReplyRule> selectGlobalRules(@Param("accountId") Long accountId,
                                                @Param("isDefault") Integer isDefault);

    @Update("UPDATE xianyu_auto_reply_rule SET xy_goods_id = #{newXyGoodsId} " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{oldXyGoodsId}")
    int replaceGoodsId(@Param("accountId") Long accountId,
                       @Param("oldXyGoodsId") String oldXyGoodsId,
                       @Param("newXyGoodsId") String newXyGoodsId);
}
