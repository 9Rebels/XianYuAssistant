package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商品自动发货配置Mapper
 */
@Mapper
public interface XianyuGoodsAutoDeliveryConfigMapper extends BaseMapper<XianyuGoodsAutoDeliveryConfig> {
    
    /**
     * 根据账号ID和商品ID查询配置
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodsId 闲鱼商品ID
     * @return 自动发货配置
     */
    @Select("SELECT id, xianyu_account_id, xianyu_goods_id, xy_goods_id, delivery_mode, auto_delivery_content, kami_config_ids, kami_delivery_template, auto_delivery_image_url, post_delivery_text, auto_confirm_shipment, delivery_delay_seconds, trigger_payment_enabled, trigger_bargain_enabled, " +
            "api_allocate_url, api_confirm_url, api_return_url, api_header_name, api_header_value, api_request_extras, api_delivery_template, api_content_path, api_allocation_id_path, rag_delay_seconds, " +
            "rule_name, match_keyword, match_type, priority, enabled, stock, stock_warn_threshold, total_delivered, today_delivered, last_delivery_time, " +
            "strftime('%Y-%m-%d %H:%M:%S', create_time) as create_time, " +
            "strftime('%Y-%m-%d %H:%M:%S', update_time) as update_time " +
            "FROM xianyu_goods_auto_delivery_config " +
            "WHERE xianyu_account_id = #{xianyuAccountId} AND xy_goods_id = #{xyGoodsId} " +
            "ORDER BY priority ASC, create_time DESC " +
            "LIMIT 1")
    XianyuGoodsAutoDeliveryConfig findByAccountIdAndGoodsId(@Param("xianyuAccountId") Long xianyuAccountId, 
                                                           @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT id, xianyu_account_id, xianyu_goods_id, xy_goods_id, delivery_mode, auto_delivery_content, kami_config_ids, kami_delivery_template, auto_delivery_image_url, post_delivery_text, auto_confirm_shipment, delivery_delay_seconds, trigger_payment_enabled, trigger_bargain_enabled, " +
            "api_allocate_url, api_confirm_url, api_return_url, api_header_name, api_header_value, api_request_extras, api_delivery_template, api_content_path, api_allocation_id_path, rag_delay_seconds, " +
            "rule_name, match_keyword, match_type, priority, enabled, stock, stock_warn_threshold, total_delivered, today_delivered, last_delivery_time, " +
            "strftime('%Y-%m-%d %H:%M:%S', create_time) as create_time, " +
            "strftime('%Y-%m-%d %H:%M:%S', update_time) as update_time " +
            "FROM xianyu_goods_auto_delivery_config " +
            "WHERE xianyu_account_id = #{xianyuAccountId} AND xy_goods_id = #{xyGoodsId} " +
            "ORDER BY priority ASC, create_time DESC")
    List<XianyuGoodsAutoDeliveryConfig> findRulesByAccountIdAndGoodsId(@Param("xianyuAccountId") Long xianyuAccountId,
                                                                       @Param("xyGoodsId") String xyGoodsId);
    
    /**
     * 根据账号ID查询所有配置
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @return 自动发货配置列表
     */
    @Select("SELECT id, xianyu_account_id, xianyu_goods_id, xy_goods_id, delivery_mode, auto_delivery_content, kami_config_ids, kami_delivery_template, auto_delivery_image_url, post_delivery_text, auto_confirm_shipment, delivery_delay_seconds, trigger_payment_enabled, trigger_bargain_enabled, " +
            "api_allocate_url, api_confirm_url, api_return_url, api_header_name, api_header_value, api_request_extras, api_delivery_template, api_content_path, api_allocation_id_path, rag_delay_seconds, " +
            "rule_name, match_keyword, match_type, priority, enabled, stock, stock_warn_threshold, total_delivered, today_delivered, last_delivery_time, " +
            "strftime('%Y-%m-%d %H:%M:%S', create_time) as create_time, " +
            "strftime('%Y-%m-%d %H:%M:%S', update_time) as update_time " +
            "FROM xianyu_goods_auto_delivery_config " +
            "WHERE xianyu_account_id = #{xianyuAccountId} " +
            "ORDER BY priority ASC, create_time DESC")
    List<XianyuGoodsAutoDeliveryConfig> findByAccountId(@Param("xianyuAccountId") Long xianyuAccountId);

    @Update("UPDATE xianyu_goods_auto_delivery_config SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    @Update("UPDATE xianyu_goods_auto_delivery_config SET stock = #{stock} WHERE id = #{id}")
    int updateStock(@Param("id") Long id, @Param("stock") Integer stock);

    @Update("UPDATE xianyu_goods_auto_delivery_config SET xy_goods_id = #{newXyGoodsId} " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{oldXyGoodsId}")
    int replaceGoodsId(@Param("accountId") Long accountId,
                       @Param("oldXyGoodsId") String oldXyGoodsId,
                       @Param("newXyGoodsId") String newXyGoodsId);

    @Update("UPDATE xianyu_goods_auto_delivery_config SET " +
            "stock = CASE WHEN stock > 0 THEN stock - 1 ELSE stock END, " +
            "total_delivered = COALESCE(total_delivered, 0) + 1, " +
            "today_delivered = CASE WHEN date(COALESCE(last_delivery_time, '1970-01-01')) = date('now', 'localtime') THEN COALESCE(today_delivered, 0) + 1 ELSE 1 END, " +
            "last_delivery_time = datetime('now', 'localtime') " +
            "WHERE id = #{id}")
    int markDeliverySuccess(@Param("id") Long id);

    @Update("UPDATE xianyu_goods_auto_delivery_config SET " +
            "stock = CASE WHEN stock > 0 THEN CASE WHEN stock >= #{quantity} THEN stock - #{quantity} ELSE 0 END ELSE stock END, " +
            "total_delivered = COALESCE(total_delivered, 0) + #{quantity}, " +
            "today_delivered = CASE WHEN date(COALESCE(last_delivery_time, '1970-01-01')) = date('now', 'localtime') THEN COALESCE(today_delivered, 0) + #{quantity} ELSE #{quantity} END, " +
            "last_delivery_time = datetime('now', 'localtime') " +
            "WHERE id = #{id}")
    int markDeliverySuccessBatch(@Param("id") Long id, @Param("quantity") int quantity);
    
    /**
     * 根据账号ID删除自动发货配置
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @return 删除的记录数量
     */
    @Delete("DELETE FROM xianyu_goods_auto_delivery_config WHERE xianyu_account_id = #{xianyuAccountId}")
    int deleteByAccountId(@Param("xianyuAccountId") Long xianyuAccountId);

    @Delete("DELETE FROM xianyu_goods_auto_delivery_config WHERE xianyu_account_id = #{xianyuAccountId} AND xy_goods_id = #{xyGoodsId}")
    int deleteByAccountIdAndGoodsId(@Param("xianyuAccountId") Long xianyuAccountId, @Param("xyGoodsId") String xyGoodsId);
}
