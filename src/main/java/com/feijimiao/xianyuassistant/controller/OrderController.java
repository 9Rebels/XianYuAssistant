package com.feijimiao.xianyuassistant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.ConfirmShipmentReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.OrderQueryReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.SoldOrderSyncReqDTO;
import com.feijimiao.xianyuassistant.controller.vo.OrderVO;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.DeliveryMessageSettingService;
import com.feijimiao.xianyuassistant.service.OrderBatchRefreshService;
import com.feijimiao.xianyuassistant.service.OrderDetailUpdateService;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.SoldOrderSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@CrossOrigin(origins = "*")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuOrderMapper xianyuOrderMapper;

    @Autowired
    private DeliveryMessageSettingService deliveryMessageSettingService;

    @Autowired
    private OrderBatchRefreshService orderBatchRefreshService;

    @Autowired
    private OrderDetailUpdateService orderDetailUpdateService;

    @Autowired
    private SoldOrderSyncService soldOrderSyncService;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    /**
     * 查询订单列表
     */
    @PostMapping("/list")
    public ResultObject<Map<String, Object>> list(@RequestBody OrderQueryReqDTO reqDTO) {
        try {
            int pageNum = normalizePageNum(reqDTO.getPageNum());
            int pageSize = normalizePageSize(reqDTO.getPageSize());
            Page<OrderVO> page = new Page<>(pageNum, pageSize);
            Page<OrderVO> result = xianyuOrderMapper.queryOrderList(
                    page,
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    reqDTO.getOrderStatus()
            );

            Map<String, Object> data = new HashMap<>();
            data.put("records", result.getRecords());
            data.put("total", result.getTotal());
            data.put("pageNum", pageNum);
            data.put("pageSize", pageSize);
            data.put("pages", result.getPages());
            return ResultObject.success(data);
        } catch (Exception e) {
            log.error("查询订单列表失败", e);
            return ResultObject.failed("查询订单列表失败: " + e.getMessage());
        }
    }

    /**
     * 确认发货
     */
    @PostMapping("/confirmShipment")
    public ResultObject<String> confirmShipment(@RequestBody ConfirmShipmentReqDTO reqDTO) {
        try {
            log.info("确认发货请求: xianyuAccountId={}, orderId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getOrderId());

            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            if (reqDTO.getOrderId() == null || reqDTO.getOrderId().isEmpty()) {
                return ResultObject.failed("订单ID不能为空");
            }

            XianyuGoodsOrder existingOrder = orderMapper.selectByAccountIdAndOrderId(
                    reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
            if (existingOrder != null && existingOrder.getConfirmState() != null && existingOrder.getConfirmState() == 1) {
                log.info("订单已确认发货，跳过重复确认: xianyuAccountId={}, orderId={}",
                        reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
                return ResultObject.success("订单已确认发货，无需重复操作");
            }

            String result = orderService.confirmShipment(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getOrderId()
            );

            if (result != null) {
                orderMapper.updateConfirmState(reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
                XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(
                        reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
                deliveryMessageSettingService.sendOrderStatusMessage(
                        DeliveryMessageSettingService.EVENT_SHIPPED, order);
                return ResultObject.success(result);
            } else {
                return ResultObject.failed("确认发货失败");
            }

        } catch (Exception e) {
            log.error("确认发货失败", e);
            return ResultObject.failed("确认发货失败: " + e.getMessage());
        }
    }

    /**
     * 批量刷新订单
     */
    @PostMapping("/batchRefresh")
    public ResultObject<Map<String, Object>> batchRefresh(@RequestBody Map<String, Object> reqBody) {
        try {
            Long accountId = Long.valueOf(reqBody.get("xianyuAccountId").toString());
            @SuppressWarnings("unchecked")
            List<String> orderIds = (List<String>) reqBody.get("orderIds");
            Boolean headless = reqBody.containsKey("headless") ? (Boolean) reqBody.get("headless") : true;

            log.info("批量刷新订单请求: accountId={}, 订单数量={}", accountId, orderIds.size());

            if (accountId == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            if (orderIds == null || orderIds.isEmpty()) {
                return ResultObject.failed("订单ID列表不能为空");
            }

            // 验证订单归属（安全加固）
            for (String orderId : orderIds) {
                XianyuOrder existingOrder = xianyuOrderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getOrderId, orderId)
                );

                // 如果订单已存在，验证是否属于该账号
                if (existingOrder != null && !existingOrder.getXianyuAccountId().equals(accountId)) {
                    log.warn("订单归属验证失败: orderId={}, 期望accountId={}, 实际accountId={}",
                        orderId, accountId, existingOrder.getXianyuAccountId());
                    return ResultObject.failed("订单 " + orderId + " 不属于该账号");
                }
            }

            // 获取账号Cookie
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                return ResultObject.failed("账号不存在");
            }

            XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
            if (cookie == null || cookie.getCookieText() == null) {
                return ResultObject.failed("账号Cookie不存在");
            }

            // 批量刷新订单
            List<OrderBatchRefreshService.OrderDetailResult> results =
                orderBatchRefreshService.batchRefreshOrders(accountId, cookie.getCookieText(), orderIds, headless);

            // 更新数据库
            int successCount = 0;
            int failCount = 0;
            List<Map<String, Object>> resultList = new ArrayList<>();

            for (OrderBatchRefreshService.OrderDetailResult result : results) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("orderId", result.getOrderId());
                resultMap.put("success", result.isSuccess());

                if (result.isSuccess()) {
                    orderDetailUpdateService.upsertOrderDetail(accountId, result);
                    successCount++;
                    resultMap.put("data", result);
                } else {
                    failCount++;
                    resultMap.put("error", result.getError());
                }

                resultList.add(resultMap);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("total", results.size());
            data.put("successCount", successCount);
            data.put("failCount", failCount);
            data.put("results", resultList);

            log.info("批量刷新订单完成: accountId={}, 成功={}, 失败={}", accountId, successCount, failCount);

            return ResultObject.success(data);
        } catch (Exception e) {
            log.error("批量刷新订单失败", e);
            return ResultObject.failed("批量刷新订单失败: " + e.getMessage());
        }
    }

    /**
     * 同步鱼小铺卖家订单列表
     */
    @PostMapping("/syncSoldOrders")
    public ResultObject<SoldOrderSyncService.SyncResult> syncSoldOrders(@RequestBody SoldOrderSyncReqDTO reqDTO) {
        try {
            if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            SoldOrderSyncService.SyncResult result =
                    soldOrderSyncService.syncSoldOrders(reqDTO.getXianyuAccountId());
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("同步鱼小铺卖家订单列表失败", e);
            return ResultObject.failed(e.getMessage());
        }
    }

    private int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
