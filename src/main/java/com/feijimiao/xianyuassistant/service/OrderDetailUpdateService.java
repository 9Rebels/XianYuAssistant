package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Applies refreshed order detail data to xianyu_order.
 */
@Service
public class OrderDetailUpdateService {

    @Autowired
    private XianyuOrderMapper xianyuOrderMapper;

    @Autowired
    private AutoDeliveryService autoDeliveryService;

    public XianyuOrder upsertOrderDetail(Long accountId, OrderBatchRefreshService.OrderDetailResult result) {
        if (accountId == null || result == null || !result.isSuccess()) {
            return null;
        }

        XianyuOrder order = xianyuOrderMapper.selectOne(
            new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getXianyuAccountId, accountId)
                .eq(XianyuOrder::getOrderId, result.getOrderId())
        );

        Integer oldStatus = order != null ? order.getOrderStatus() : null;
        String oldStatusText = order != null ? order.getOrderStatusText() : null;

        if (order == null) {
            order = new XianyuOrder();
            order.setXianyuAccountId(accountId);
            order.setOrderId(result.getOrderId());
        }

        setIfPresent(result.getOrderStatus(), order::setOrderStatus);
        setIfPresent(result.getStatusText(), order::setOrderStatusText);
        setIfPresent(result.getXyGoodsId(), order::setXyGoodsId);
        setIfPresent(result.getItemTitle(), order::setGoodsTitle);
        setIfPresent(result.getBuyerUserId(), order::setBuyerUserId);
        setIfPresent(result.getBuyerUserName(), order::setBuyerUserName);
        setIfPresent(result.getOrderAmount(), order::setOrderAmount);
        setIfPresent(result.getPrice(), order::setOrderAmountText);
        setIfPresent(result.getOrderCreateTime(), order::setOrderCreateTime);
        setIfPresent(result.getOrderPayTime(), order::setOrderPayTime);
        setIfPresent(result.getOrderDeliveryTime(), order::setOrderDeliveryTime);
        setIfPresent(result.getReceiverName(), order::setReceiverName);
        setIfPresent(result.getReceiverPhone(), order::setReceiverPhone);
        setIfPresent(result.getReceiverAddress(), order::setReceiverAddress);
        setIfPresent(result.getReceiverCity(), order::setReceiverCity);

        if (order.getId() == null) {
            xianyuOrderMapper.insert(order);
        } else {
            xianyuOrderMapper.updateById(order);
        }
        compensateApiDeliveryIfNeeded(accountId, order, oldStatus, oldStatusText);
        return order;
    }

    private void compensateApiDeliveryIfNeeded(Long accountId, XianyuOrder order,
                                               Integer oldStatus, String oldStatusText) {
        if (order == null || isReturnableStatus(oldStatus, oldStatusText)) {
            return;
        }
        if (!isReturnableStatus(order.getOrderStatus(), order.getOrderStatusText())) {
            return;
        }
        String reason = "订单状态变更为" + safeStatus(order.getOrderStatusText());
        autoDeliveryService.returnApiDeliveryForOrder(accountId, order.getOrderId(), reason);
    }

    private <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value instanceof String text) {
            if (!text.isBlank()) {
                setter.accept(value);
            }
            return;
        }
        if (value != null) {
            setter.accept(value);
        }
    }

    private boolean isReturnableStatus(Integer status, String statusText) {
        if (status != null && status == 5) {
            return true;
        }
        if (statusText == null || statusText.isBlank()) {
            return false;
        }
        return statusText.contains("关闭")
                || statusText.contains("取消")
                || statusText.contains("退款")
                || statusText.contains("退货");
    }

    private String safeStatus(String statusText) {
        return statusText == null || statusText.isBlank() ? "取消/关闭/退款" : statusText.trim();
    }
}
