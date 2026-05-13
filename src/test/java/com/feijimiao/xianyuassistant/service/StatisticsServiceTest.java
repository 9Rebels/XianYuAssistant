package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    @Test
    void overallStatisticsCountsPendingDeliverySeparatelyFromShipped() {
        StatisticsService service = serviceWithOrders(List.of(
            order("order-1", 3, "1048981205041", "重生之我在某鱼卖东西", 2500L, "北京北京市海淀区圆明园西路2号"),
            order("order-2", 3, null, "测试商品", 1L, "重庆重庆市九龙坡区谢家湾街道")
        ));

        Map<String, Object> overall = service.getOverallStatistics(null);

        assertEquals(2, overall.get("totalOrders"));
        assertEquals(0L, overall.get("pendingDeliveryOrders"));
        assertEquals(2L, overall.get("paidOrders"));
        assertEquals(2L, overall.get("shippedOrders"));
        assertEquals(2501L, overall.get("totalAmount"));
    }

    @Test
    void regionDistributionFallsBackToReceiverAddressCity() {
        StatisticsService service = serviceWithOrders(List.of(
            order("order-1", 3, "1048981205041", "重生之我在某鱼卖东西", 2500L, "北京北京市海淀区圆明园西路2号"),
            order("order-2", 3, "1048981205042", "测试商品", 1L, "付** 150****0509 重庆重庆市九龙坡区******")
        ));

        List<StatisticsService.RegionData> regions = service.getRegionDistribution(null, 5);

        assertEquals(2, regions.size());
        Map<String, Long> byCity = regions.stream()
            .collect(Collectors.toMap(StatisticsService.RegionData::getCity, StatisticsService.RegionData::getOrderCount));
        assertEquals(1L, byCity.get("北京市"));
        assertEquals(1L, byCity.get("重庆市"));
    }

    @Test
    void goodsRankingUsesGoodsTitleWhenGoodsIdIsMissing() {
        StatisticsService service = serviceWithOrders(List.of(
            order("order-1", 3, null, "测试商品", 1L, "北京北京市海淀区圆明园西路2号")
        ));

        List<StatisticsService.GoodsRankingData> ranking = service.getGoodsRanking(null, 5);

        assertFalse(ranking.isEmpty());
        assertEquals("测试商品", ranking.get(0).getGoodsTitle());
        assertEquals(1L, ranking.get(0).getOrderCount());
        assertEquals(1L, ranking.get(0).getTotalAmount());
    }

    private StatisticsService serviceWithOrders(List<XianyuOrder> orders) {
        StatisticsService service = new StatisticsService();
        XianyuOrderMapper orderMapper = mock(XianyuOrderMapper.class);
        when(orderMapper.selectList(any())).thenReturn(orders);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        return service;
    }

    private XianyuOrder order(String orderId, Integer status, String goodsId, String title,
                              Long amount, String address) {
        XianyuOrder order = new XianyuOrder();
        order.setOrderId(orderId);
        order.setOrderStatus(status);
        order.setXyGoodsId(goodsId);
        order.setGoodsTitle(title);
        order.setOrderAmount(amount);
        order.setReceiverAddress(address);
        order.setOrderCreateTime(1777949766000L);
        return order;
    }
}
