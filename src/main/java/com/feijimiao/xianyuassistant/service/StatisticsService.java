package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计服务
 *
 * 功能：
 * - 订单趋势分析（按日期统计）
 * - 地区分布统计（按城市）
 * - 商品销售排行
 * - 订单状态分布
 */
@Slf4j
@Service
public class StatisticsService {

    @Autowired
    private XianyuOrderMapper orderMapper;

    /**
     * 订单趋势数据点
     */
    @Data
    public static class TrendDataPoint {
        private String date;           // 日期（yyyy-MM-dd）
        private Long totalOrders;      // 订单总数
        private Long pendingDeliveryOrders; // 待发货订单数
        private Long paidOrders;       // 已付款订单数
        private Long shippedOrders;    // 已发货订单数
        private Long completedOrders;  // 已完成订单数
        private Long cancelledOrders;  // 已取消订单数
    }

    /**
     * 地区分布数据
     */
    @Data
    public static class RegionData {
        private String city;           // 城市
        private Long orderCount;       // 订单数量
        private Double percentage;     // 占比
    }

    /**
     * 商品销售排行数据
     */
    @Data
    public static class GoodsRankingData {
        private String xyGoodsId;      // 商品ID
        private String goodsTitle;     // 商品标题
        private Long orderCount;       // 订单数量
        private Long totalAmount;      // 总金额（分）
        private Double totalAmountYuan; // 总金额（元）
    }

    /**
     * 订单状态分布数据
     */
    @Data
    public static class StatusDistribution {
        private Integer status;        // 状态码
        private String statusText;     // 状态文本
        private Long count;            // 数量
        private Double percentage;     // 占比
    }

    /**
     * 获取订单趋势（按日期）
     *
     * @param accountId 账号ID（可选）
     * @param startDate 开始日期（时间戳，毫秒）
     * @param endDate 结束日期（时间戳，毫秒）
     * @param days 最近N天（如果startDate和endDate为空，则使用此参数，默认30天）
     * @return 趋势数据列表
     */
    public List<TrendDataPoint> getOrderTrend(Long accountId, Long startDate, Long endDate, Integer days) {
        try {
            // 确定时间范围
            if (startDate == null || endDate == null) {
                if (days == null || days <= 0) {
                    days = 30;
                }
                endDate = System.currentTimeMillis();
                startDate = endDate - (days * 24L * 60 * 60 * 1000);
            }

            log.info("查询订单趋势: accountId={}, startDate={}, endDate={}", accountId, startDate, endDate);

            // 查询订单
            LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
            if (accountId != null) {
                wrapper.eq(XianyuOrder::getXianyuAccountId, accountId);
            }
            wrapper.ge(XianyuOrder::getOrderCreateTime, startDate);
            wrapper.le(XianyuOrder::getOrderCreateTime, endDate);

            List<XianyuOrder> orders = orderMapper.selectList(wrapper);

            // 按日期分组统计
            Map<String, List<XianyuOrder>> ordersByDate = orders.stream()
                .collect(Collectors.groupingBy(order -> {
                    long timestamp = order.getOrderCreateTime();
                    LocalDate date = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                    return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                }));

            // 生成趋势数据
            List<TrendDataPoint> trendData = new ArrayList<>();
            for (Map.Entry<String, List<XianyuOrder>> entry : ordersByDate.entrySet()) {
                String date = entry.getKey();
                List<XianyuOrder> dayOrders = entry.getValue();

                TrendDataPoint point = new TrendDataPoint();
                point.setDate(date);
                point.setTotalOrders((long) dayOrders.size());
                point.setPendingDeliveryOrders(countStatus(dayOrders, 2));
                point.setPaidOrders(dayOrders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() >= 2).count());
                point.setShippedOrders(countStatus(dayOrders, 3));
                point.setCompletedOrders(countStatus(dayOrders, 4));
                point.setCancelledOrders(countStatus(dayOrders, 5));

                trendData.add(point);
            }

            // 按日期排序
            trendData.sort(Comparator.comparing(TrendDataPoint::getDate));

            log.info("订单趋势查询完成: 数据点数量={}", trendData.size());
            return trendData;

        } catch (Exception e) {
            log.error("查询订单趋势失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取地区分布统计
     *
     * @param accountId 账号ID（可选）
     * @param topN 返回前N个城市（默认10）
     * @return 地区分布数据列表
     */
    public List<RegionData> getRegionDistribution(Long accountId, Integer topN) {
        try {
            if (topN == null || topN <= 0) {
                topN = 10;
            }

            log.info("查询地区分布: accountId={}, topN={}", accountId, topN);

            // 查询订单
            LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
            if (accountId != null) {
                wrapper.eq(XianyuOrder::getXianyuAccountId, accountId);
            }

            List<XianyuOrder> orders = orderMapper.selectList(wrapper);

            if (orders.isEmpty()) {
                return new ArrayList<>();
            }

            // 按城市分组统计
            Map<String, Long> cityCount = orders.stream()
                .map(this::resolveCity)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(
                    city -> city,
                    Collectors.counting()
                ));

            if (cityCount.isEmpty()) {
                return new ArrayList<>();
            }

            // 计算总数
            long totalCount = cityCount.values().stream().mapToLong(Long::longValue).sum();

            // 转换为RegionData并排序
            List<RegionData> regionData = cityCount.entrySet().stream()
                .map(entry -> {
                    RegionData data = new RegionData();
                    data.setCity(entry.getKey());
                    data.setOrderCount(entry.getValue());
                    data.setPercentage(entry.getValue() * 100.0 / totalCount);
                    return data;
                })
                .sorted(Comparator.comparing(RegionData::getOrderCount).reversed())
                .limit(topN)
                .collect(Collectors.toList());

            log.info("地区分布查询完成: 城市数量={}", regionData.size());
            return regionData;

        } catch (Exception e) {
            log.error("查询地区分布失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取商品销售排行
     *
     * @param accountId 账号ID（可选）
     * @param topN 返回前N个商品（默认10）
     * @return 商品排行数据列表
     */
    public List<GoodsRankingData> getGoodsRanking(Long accountId, Integer topN) {
        try {
            if (topN == null || topN <= 0) {
                topN = 10;
            }

            log.info("查询商品销售排行: accountId={}, topN={}", accountId, topN);

            // 查询订单
            LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
            if (accountId != null) {
                wrapper.eq(XianyuOrder::getXianyuAccountId, accountId);
            }

            List<XianyuOrder> orders = orderMapper.selectList(wrapper);

            if (orders.isEmpty()) {
                return new ArrayList<>();
            }

            // 按商品分组统计
            Map<String, List<XianyuOrder>> ordersByGoods = orders.stream()
                .filter(order -> hasText(resolveGoodsKey(order)))
                .collect(Collectors.groupingBy(this::resolveGoodsKey));

            if (ordersByGoods.isEmpty()) {
                return new ArrayList<>();
            }

            // 转换为GoodsRankingData并排序
            List<GoodsRankingData> rankingData = ordersByGoods.entrySet().stream()
                .map(entry -> {
                    String goodsKey = entry.getKey();
                    List<XianyuOrder> goodsOrders = entry.getValue();
                    XianyuOrder sample = goodsOrders.get(0);

                    GoodsRankingData data = new GoodsRankingData();
                    data.setXyGoodsId(goodsKey);
                    data.setGoodsTitle(hasText(sample.getGoodsTitle()) ? sample.getGoodsTitle() : goodsKey);
                    data.setOrderCount((long) goodsOrders.size());

                    // 计算总金额
                    long totalAmount = goodsOrders.stream()
                        .filter(o -> o.getOrderAmount() != null)
                        .mapToLong(XianyuOrder::getOrderAmount)
                        .sum();
                    data.setTotalAmount(totalAmount);
                    data.setTotalAmountYuan(totalAmount / 100.0);

                    return data;
                })
                .sorted(Comparator.comparing(GoodsRankingData::getOrderCount)
                    .thenComparing(GoodsRankingData::getTotalAmount)
                    .reversed())
                .limit(topN)
                .collect(Collectors.toList());

            log.info("商品销售排行查询完成: 商品数量={}", rankingData.size());
            return rankingData;

        } catch (Exception e) {
            log.error("查询商品销售排行失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取订单状态分布
     *
     * @param accountId 账号ID（可选）
     * @return 状态分布数据列表
     */
    public List<StatusDistribution> getStatusDistribution(Long accountId) {
        try {
            log.info("查询订单状态分布: accountId={}", accountId);

            // 查询订单
            LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
            if (accountId != null) {
                wrapper.eq(XianyuOrder::getXianyuAccountId, accountId);
            }
            wrapper.isNotNull(XianyuOrder::getOrderStatus);

            List<XianyuOrder> orders = orderMapper.selectList(wrapper);

            if (orders.isEmpty()) {
                return new ArrayList<>();
            }

            // 按状态分组统计
            Map<Integer, Long> statusCount = orders.stream()
                .collect(Collectors.groupingBy(
                    XianyuOrder::getOrderStatus,
                    Collectors.counting()
                ));

            // 计算总数
            long totalCount = orders.size();

            // 状态文本映射
            Map<Integer, String> statusTextMap = new HashMap<>();
            statusTextMap.put(1, "待付款");
            statusTextMap.put(2, "待发货");
            statusTextMap.put(3, "已发货");
            statusTextMap.put(4, "已完成");
            statusTextMap.put(5, "已取消");

            // 转换为StatusDistribution
            List<StatusDistribution> distribution = statusCount.entrySet().stream()
                .map(entry -> {
                    StatusDistribution data = new StatusDistribution();
                    data.setStatus(entry.getKey());
                    data.setStatusText(statusTextMap.getOrDefault(entry.getKey(), "未知"));
                    data.setCount(entry.getValue());
                    data.setPercentage(entry.getValue() * 100.0 / totalCount);
                    return data;
                })
                .sorted(Comparator.comparing(StatusDistribution::getStatus))
                .collect(Collectors.toList());

            log.info("订单状态分布查询完成: 状态数量={}", distribution.size());
            return distribution;

        } catch (Exception e) {
            log.error("查询订单状态分布失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取综合统计数据
     *
     * @param accountId 账号ID（可选）
     * @return 综合统计数据
     */
    public Map<String, Object> getOverallStatistics(Long accountId) {
        try {
            log.info("查询综合统计: accountId={}", accountId);

            // 查询订单
            LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
            if (accountId != null) {
                wrapper.eq(XianyuOrder::getXianyuAccountId, accountId);
            }

            List<XianyuOrder> orders = orderMapper.selectList(wrapper);

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalOrders", orders.size());
            statistics.put("pendingDeliveryOrders", countStatus(orders, 2));
            statistics.put("paidOrders", orders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() >= 2).count());
            statistics.put("shippedOrders", countStatus(orders, 3));
            statistics.put("completedOrders", countStatus(orders, 4));
            statistics.put("cancelledOrders", countStatus(orders, 5));

            // 计算总金额
            long totalAmount = orders.stream()
                .filter(o -> o.getOrderAmount() != null)
                .mapToLong(XianyuOrder::getOrderAmount)
                .sum();
            statistics.put("totalAmount", totalAmount);
            statistics.put("totalAmountYuan", totalAmount / 100.0);

            // 计算平均金额
            double avgAmount = orders.stream()
                .filter(o -> o.getOrderAmount() != null)
                .mapToLong(XianyuOrder::getOrderAmount)
                .average()
                .orElse(0.0);
            statistics.put("avgAmount", (long) avgAmount);
            statistics.put("avgAmountYuan", avgAmount / 100.0);

            log.info("综合统计查询完成");
            return statistics;

        } catch (Exception e) {
            log.error("查询综合统计失败", e);
            return new HashMap<>();
        }
    }

    private long countStatus(List<XianyuOrder> orders, int status) {
        return orders.stream()
            .filter(order -> order.getOrderStatus() != null && order.getOrderStatus() == status)
            .count();
    }

    private String resolveGoodsKey(XianyuOrder order) {
        if (hasText(order.getXyGoodsId())) {
            return order.getXyGoodsId().trim();
        }
        if (hasText(order.getGoodsTitle())) {
            return order.getGoodsTitle().trim();
        }
        return "";
    }

    private String resolveCity(XianyuOrder order) {
        if (hasText(order.getReceiverCity())) {
            String receiverCity = order.getReceiverCity().trim();
            String municipality = normalizeMunicipality(receiverCity);
            return hasText(municipality) ? municipality : receiverCity;
        }
        return extractCityFromAddress(order.getReceiverAddress());
    }

    private String extractCityFromAddress(String address) {
        if (!hasText(address)) {
            return "";
        }

        String normalized = normalizeAddressForCity(address);
        String municipality = normalizeMunicipality(normalized);
        if (hasText(municipality)) {
            return municipality;
        }

        int cityEnd = normalized.indexOf('市');
        if (cityEnd < 1) {
            return "";
        }

        int cityStart = 0;
        int provinceEnd = normalized.lastIndexOf('省', cityEnd);
        int autonomousRegionEnd = normalized.lastIndexOf("自治区", cityEnd);
        if (provinceEnd >= 0) {
            cityStart = provinceEnd + 1;
        } else if (autonomousRegionEnd >= 0) {
            cityStart = autonomousRegionEnd + "自治区".length();
        }

        String city = normalized.substring(cityStart, cityEnd + 1);
        return city.length() > 1 ? city : "";
    }

    private String normalizeAddressForCity(String address) {
        String normalized = address.replaceAll("\\s+", "");
        int municipalityStart = firstMunicipalityIndex(normalized);
        if (municipalityStart >= 0) {
            return normalized.substring(municipalityStart);
        }
        return normalized;
    }

    private int firstMunicipalityIndex(String text) {
        int first = -1;
        for (String municipality : List.of("北京", "上海", "天津", "重庆")) {
            int index = text.indexOf(municipality);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
    }

    private String normalizeMunicipality(String text) {
        String value = text.trim();
        if (value.startsWith("北京")) {
            return "北京市";
        }
        if (value.startsWith("上海")) {
            return "上海市";
        }
        if (value.startsWith("天津")) {
            return "天津市";
        }
        if (value.startsWith("重庆")) {
            return "重庆市";
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
