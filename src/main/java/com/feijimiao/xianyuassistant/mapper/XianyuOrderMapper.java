package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feijimiao.xianyuassistant.controller.vo.OrderVO;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 闲鱼订单Mapper
 */
@Mapper
public interface XianyuOrderMapper extends BaseMapper<XianyuOrder> {

    @Select("SELECT * FROM xianyu_order WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} LIMIT 1")
    XianyuOrder selectByAccountIdAndOrderId(@Param("accountId") Long accountId, @Param("orderId") String orderId);
    
    /**
     * 分页查询订单列表（联表查询账号备注和自动发货状态）
     * 
     * @param page 分页对象
     * @param xianyuAccountId 账号ID
     * @param xyGoodsId 商品ID
     * @param orderStatus 订单状态
     * @return 订单列表
     */
    @Select("<script>" +
            "SELECT * FROM ( " +
            "  SELECT " +
            "    o.id, " +
            "    o.xianyu_account_id AS xianyuAccountId, " +
            "    a.account_note AS accountRemark, " +
            "    o.order_id AS orderId, " +
            "    o.goods_title AS goodsTitle, " +
            "    o.s_id AS sId, " +
            "    o.order_create_time AS createTime, " +
            "    o.order_pay_time AS payTime, " +
            "    o.order_delivery_time AS deliveryTime, " +
            "    COALESCE(r.auto_delivery_success, 0) AS autoDeliverySuccess, " +
            "    o.order_status AS orderStatus, " +
            "    o.order_status_text AS orderStatusText, " +
            "    o.buyer_user_name AS buyerUserName, " +
            "    o.order_amount_text AS orderAmountText, " +
            "    o.xy_goods_id AS xyGoodsId, " +
            "    o.receiver_name AS receiverName, " +
            "    o.receiver_phone AS receiverPhone, " +
            "    o.receiver_address AS receiverAddress, " +
            "    o.receiver_city AS receiverCity " +
            "  FROM xianyu_order o " +
            "  LEFT JOIN xianyu_account a ON o.xianyu_account_id = a.id " +
            "  LEFT JOIN ( " +
            "    SELECT xianyu_account_id, order_id, MAX(CASE WHEN state = 1 THEN 1 ELSE 0 END) AS auto_delivery_success " +
            "    FROM xianyu_goods_order " +
            "    WHERE order_id IS NOT NULL AND order_id != '' " +
            "    GROUP BY xianyu_account_id, order_id " +
            "  ) r ON o.xianyu_account_id = r.xianyu_account_id AND o.order_id = r.order_id " +
            "  UNION ALL " +
            "  SELECT " +
            "    -r.id AS id, " +
            "    r.xianyu_account_id AS xianyuAccountId, " +
            "    a.account_note AS accountRemark, " +
            "    r.order_id AS orderId, " +
            "    COALESCE(od.goods_title, g.title, r.xy_goods_id) AS goodsTitle, " +
            "    COALESCE(od.s_id, r.sid) AS sId, " +
            "    COALESCE(od.order_create_time, strftime('%s', COALESCE(r.create_time, datetime('now', 'localtime'))) * 1000) AS createTime, " +
            "    od.order_pay_time AS payTime, " +
            "    od.order_delivery_time AS deliveryTime, " +
            "    CASE WHEN r.state = 1 THEN 1 ELSE 0 END AS autoDeliverySuccess, " +
            "    COALESCE(od.order_status, CASE WHEN r.confirm_state = 1 THEN 3 ELSE 2 END) AS orderStatus, " +
            "    COALESCE(od.order_status_text, CASE WHEN r.confirm_state = 1 THEN '已发货' ELSE '待发货' END) AS orderStatusText, " +
            "    COALESCE(od.buyer_user_name, r.buyer_user_name) AS buyerUserName, " +
            "    od.order_amount_text AS orderAmountText, " +
            "    COALESCE(od.xy_goods_id, r.xy_goods_id) AS xyGoodsId, " +
            "    od.receiver_name AS receiverName, " +
            "    od.receiver_phone AS receiverPhone, " +
            "    od.receiver_address AS receiverAddress, " +
            "    od.receiver_city AS receiverCity " +
            "  FROM xianyu_goods_order r " +
            "  LEFT JOIN xianyu_account a ON r.xianyu_account_id = a.id " +
            "  LEFT JOIN xianyu_goods g ON r.xianyu_account_id = g.xianyu_account_id AND r.xy_goods_id = g.xy_good_id " +
            "  LEFT JOIN xianyu_order od ON r.xianyu_account_id = od.xianyu_account_id AND r.order_id = od.order_id " +
            "  WHERE r.order_id IS NOT NULL AND r.order_id != '' " +
            "    AND NOT EXISTS ( " +
            "      SELECT 1 FROM xianyu_order o2 " +
            "      WHERE o2.xianyu_account_id = r.xianyu_account_id AND o2.order_id = r.order_id " +
            "    ) " +
            ") merged " +
            "WHERE 1=1 " +
            "  AND NOT EXISTS ( " +
            "    SELECT 1 FROM xianyu_goods gx " +
            "    WHERE gx.xy_good_id = merged.xyGoodsId " +
            "      AND gx.xianyu_account_id IS NOT NULL " +
            "      AND gx.xianyu_account_id != merged.xianyuAccountId " +
            "  ) " +
            "<if test='xianyuAccountId != null'> " +
            "  AND merged.xianyuAccountId = #{xianyuAccountId} " +
            "</if> " +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'> " +
            "  AND merged.xyGoodsId = #{xyGoodsId} " +
            "</if> " +
            "<if test='orderStatus != null'> " +
            "  AND merged.orderStatus = #{orderStatus} " +
            "</if> " +
            "ORDER BY merged.createTime DESC " +
            "</script>")
    Page<OrderVO> queryOrderList(
            Page<OrderVO> page,
            @Param("xianyuAccountId") Long xianyuAccountId,
            @Param("xyGoodsId") String xyGoodsId,
            @Param("orderStatus") Integer orderStatus
    );
}
