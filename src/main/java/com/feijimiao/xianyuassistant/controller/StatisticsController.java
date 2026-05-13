package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/statistics")
@CrossOrigin(origins = "*")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 获取订单趋势
     */
    @GetMapping("/order-trend")
    public ResultObject<List<StatisticsService.TrendDataPoint>> getOrderTrend(
            @RequestParam(required = false) Long xianyuAccountId,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate,
            @RequestParam(required = false) Integer days) {
        try {
            log.info("查询订单趋势: accountId={}, startDate={}, endDate={}, days={}",
                xianyuAccountId, startDate, endDate, days);

            List<StatisticsService.TrendDataPoint> trendData =
                statisticsService.getOrderTrend(xianyuAccountId, startDate, endDate, days);

            return ResultObject.success(trendData);
        } catch (Exception e) {
            log.error("查询订单趋势失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取地区分布
     */
    @GetMapping("/region-distribution")
    public ResultObject<List<StatisticsService.RegionData>> getRegionDistribution(
            @RequestParam(required = false) Long xianyuAccountId,
            @RequestParam(required = false, defaultValue = "10") Integer topN) {
        try {
            log.info("查询地区分布: accountId={}, topN={}", xianyuAccountId, topN);

            List<StatisticsService.RegionData> regionData =
                statisticsService.getRegionDistribution(xianyuAccountId, topN);

            return ResultObject.success(regionData);
        } catch (Exception e) {
            log.error("查询地区分布失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取商品销售排行
     */
    @GetMapping("/goods-ranking")
    public ResultObject<List<StatisticsService.GoodsRankingData>> getGoodsRanking(
            @RequestParam(required = false) Long xianyuAccountId,
            @RequestParam(required = false, defaultValue = "10") Integer topN) {
        try {
            log.info("查询商品销售排行: accountId={}, topN={}", xianyuAccountId, topN);

            List<StatisticsService.GoodsRankingData> rankingData =
                statisticsService.getGoodsRanking(xianyuAccountId, topN);

            return ResultObject.success(rankingData);
        } catch (Exception e) {
            log.error("查询商品销售排行失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取订单状态分布
     */
    @GetMapping("/status-distribution")
    public ResultObject<List<StatisticsService.StatusDistribution>> getStatusDistribution(
            @RequestParam(required = false) Long xianyuAccountId) {
        try {
            log.info("查询订单状态分布: accountId={}", xianyuAccountId);

            List<StatisticsService.StatusDistribution> distribution =
                statisticsService.getStatusDistribution(xianyuAccountId);

            return ResultObject.success(distribution);
        } catch (Exception e) {
            log.error("查询订单状态分布失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取综合统计数据
     */
    @GetMapping("/overall")
    public ResultObject<Map<String, Object>> getOverallStatistics(
            @RequestParam(required = false) Long xianyuAccountId) {
        try {
            log.info("查询综合统计: accountId={}", xianyuAccountId);

            Map<String, Object> statistics =
                statisticsService.getOverallStatistics(xianyuAccountId);

            return ResultObject.success(statistics);
        } catch (Exception e) {
            log.error("查询综合统计失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取完整的统计数据（包含所有维度）
     */
    @GetMapping("/complete")
    public ResultObject<Map<String, Object>> getCompleteStatistics(
            @RequestParam(required = false) Long xianyuAccountId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false, defaultValue = "10") Integer topN) {
        try {
            log.info("查询完整统计数据: accountId={}, days={}, topN={}", xianyuAccountId, days, topN);

            Map<String, Object> result = new HashMap<>();

            // 综合统计
            result.put("overall", statisticsService.getOverallStatistics(xianyuAccountId));

            // 订单趋势
            result.put("trend", statisticsService.getOrderTrend(xianyuAccountId, null, null, days));

            // 地区分布
            result.put("regionDistribution", statisticsService.getRegionDistribution(xianyuAccountId, topN));

            // 商品排行
            result.put("goodsRanking", statisticsService.getGoodsRanking(xianyuAccountId, topN));

            // 状态分布
            result.put("statusDistribution", statisticsService.getStatusDistribution(xianyuAccountId));

            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("查询完整统计数据失败", e);
            return ResultObject.failed("查询失败: " + e.getMessage());
        }
    }
}
