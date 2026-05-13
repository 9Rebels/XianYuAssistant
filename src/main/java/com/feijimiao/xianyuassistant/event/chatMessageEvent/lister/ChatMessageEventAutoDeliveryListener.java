package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.AutoDeliveryRuleMatcher;
import com.feijimiao.xianyuassistant.service.BargainFreeShippingService;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.OrderAutoRefreshService;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 自动发货事件监听器
 *
 * <p>监听 {@link ChatMessageReceivedEvent} 事件，判断是否需要触发自动发货</p>
 *
 * <p>触发条件：</p>
 * <ul>
 *   <li>contentType = 26（已付款待发货类型）</li>
 *   <li>msgContent 包含 "[已付款，待发货]" 或 "[我已付款，等待你发货]"</li>
 * </ul>
 *
 * <p>职责：事件过滤 + 订单记录创建，发货执行委派给 {@link AutoDeliveryService#executeDelivery}</p>
 */
@Slf4j
@Component
public class ChatMessageEventAutoDeliveryListener {

    private static final String TRIGGER_PAYMENT = "payment";
    private static final String TRIGGER_BARGAIN = "bargain";
    private static final int ORDER_STATUS_PENDING_SHIPMENT = 2;
    private static final String ORDER_STATUS_PENDING_SHIPMENT_TEXT = "待发货";
    private static final Set<String> WAITING_BARGAIN_TITLES = Set.of("我已小刀，待刀成", "我已小刀,待刀成");
    private static final Set<String> READY_TO_SHIP_TITLES = Set.of("我已成功小刀，待发货", "我已成功小刀,待发货");

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuOrderMapper xianyuOrderMapper;

    @Autowired
    private GoodsInfoService goodsInfoService;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private AutoDeliveryService autoDeliveryService;

    @Autowired
    private BargainFreeShippingService bargainFreeShippingService;

    @Autowired
    private OrderAutoRefreshService orderAutoRefreshService;

    @Autowired
    private OperationLogService operationLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessageData message = event.getMessageData();
        Long accountId = message.getXianyuAccountId();

        log.info("【账号{}】[AutoDeliveryListener]收到事件: pnmId={}, contentType={}, xyGoodsId={}, sId={}, orderId={}",
                accountId, message.getPnmId(), message.getContentType(),
                message.getXyGoodsId(), message.getSId(), message.getOrderId());

        try {
            BargainCardAction bargainCardAction = resolveBargainCardAction(message);
            if (bargainCardAction == BargainCardAction.WAITING_BARGAIN) {
                handleWaitingBargainCard(message);
                return;
            }

            String triggerSource = resolveTriggerSource(message, bargainCardAction);
            if (triggerSource == null) {
                return;
            }

            if (message.getXyGoodsId() == null || message.getSId() == null) {
                log.warn("【账号{}】消息缺少商品ID或会话ID，无法触发自动发货: pnmId={}", accountId, message.getPnmId());
                return;
            }

            String buyerUserName = message.getSenderUserName();
            log.info("【账号{}】检测到自动发货触发消息: source={}, xyGoodsId={}, buyerUserId={}, orderId={}",
                    accountId, triggerSource, message.getXyGoodsId(), message.getSenderUserId(), message.getOrderId());

            XianyuGoodsInfo goodsInfo = resolveGoodsInfo(accountId, message);
            if (goodsInfo == null) {
                return;
            }
            saveOrderSummary(accountId, goodsInfo, message);

            Long recordId = createOrderRecord(accountId, goodsInfo.getId(), message, triggerSource);
            if (recordId == null) {
                return;
            }
            logAutoDeliveryEvent(message, recordId, "检测到自动发货触发消息并创建发货记录", OperationConstants.Status.SUCCESS, null);
            if (!hasActiveDeliveryRule(accountId, message.getXyGoodsId(), triggerSource, message.getMsgContent())) {
                logAutoDeliveryEvent(message, recordId, "自动发货已跳过：商品未开启或未匹配到可用规则", OperationConstants.Status.PARTIAL, null);
                return;
            }

            logAutoDeliveryEvent(message, recordId, "自动发货规则已命中，开始执行发货", OperationConstants.Status.SUCCESS, null);
            int buyQuantity = parseBuyQuantity(message);
            autoDeliveryService.executeDelivery(
                    recordId, accountId, message.getXyGoodsId(), message.getSId(),
                    message.getOrderId(), buyerUserName, true, buyQuantity);

        } catch (Exception e) {
            log.error("【账号{}】处理自动发货异常: pnmId={}", accountId, message.getPnmId(), e);
            logAutoDeliveryEvent(message, null, "处理自动发货触发消息异常", OperationConstants.Status.FAIL, e.getMessage());
        }
    }

    private void handleWaitingBargainCard(ChatMessageData message) {
        Long accountId = message.getXianyuAccountId();
        if (!hasText(message.getXyGoodsId()) || !hasText(message.getOrderId()) || !hasText(message.getSenderUserId())) {
            logAutoDeliveryEvent(message, null, "检测到小刀待刀成卡片，但免拼参数缺失", OperationConstants.Status.FAIL, null);
            return;
        }
        if (!hasActiveDeliveryRule(accountId, message.getXyGoodsId(), TRIGGER_BARGAIN, message.getMsgContent())) {
            logAutoDeliveryEvent(message, null, "检测到小刀待刀成卡片，但商品未开启小刀自动发货规则，跳过免拼", OperationConstants.Status.PARTIAL, null);
            return;
        }
        BargainFreeShippingService.FreeShippingResult result = bargainFreeShippingService.freeShipping(
                new BargainFreeShippingService.FreeShippingRequest(
                        accountId, message.getOrderId(), message.getXyGoodsId(), message.getSenderUserId()));
        if (result != null && result.success()) {
            logAutoDeliveryEvent(message, null, "检测到小刀待刀成卡片，免拼成功，等待成功小刀待发货卡片", OperationConstants.Status.SUCCESS, null);
        } else {
            logAutoDeliveryEvent(message, null, "检测到小刀待刀成卡片，免拼失败", OperationConstants.Status.FAIL,
                    result != null ? result.message() : "免拼服务未返回结果");
        }
    }

    private void logAutoDeliveryEvent(ChatMessageData message, Long recordId, String desc, int status, String errorMessage) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("recordId", recordId);
            params.put("pnmId", normalizeText(message.getPnmId()));
            params.put("sId", normalizeText(message.getSId()));
            params.put("xyGoodsId", normalizeText(message.getXyGoodsId()));
            params.put("orderId", normalizeText(message.getOrderId()));
            params.put("buyerUserId", normalizeText(message.getSenderUserId()));
            params.put("buyerUserName", normalizeText(message.getSenderUserName()));
            params.put("contentType", message.getContentType());
            params.put("messageLength", message.getMsgContent() != null ? message.getMsgContent().length() : 0);
            operationLogService.log(
                    message.getXianyuAccountId(),
                    OperationConstants.Type.SYNC,
                    OperationConstants.Module.AUTO_DELIVERY,
                    desc,
                    status,
                    OperationConstants.TargetType.ORDER,
                    recordId != null ? String.valueOf(recordId) : normalizeText(message.getOrderId()),
                    objectMapper.writeValueAsString(params),
                    null,
                    errorMessage,
                    null);
        } catch (Exception e) {
            log.warn("【账号{}】记录自动发货触发操作日志失败: pnmId={}", message.getXianyuAccountId(), message.getPnmId(), e);
        }
    }

    private boolean isPaymentMessage(ChatMessageData message) {
        if (message.getContentType() == null || message.getContentType() != 26) {
            return false;
        }
        if (message.getMsgContent() == null) {
            return false;
        }
        return message.getMsgContent().contains("[已付款，待发货]")
                || message.getMsgContent().contains("[我已付款，等待你发货]");
    }

    private String resolveTriggerSource(ChatMessageData message, BargainCardAction bargainCardAction) {
        if (isPaymentMessage(message)) {
            return TRIGGER_PAYMENT;
        }
        if (bargainCardAction == BargainCardAction.READY_TO_SHIP) {
            return TRIGGER_BARGAIN;
        }
        return null;
    }

    private BargainCardAction resolveBargainCardAction(ChatMessageData message) {
        String title = extractBargainCardTitle(message.getCompleteMsg());
        if (title == null) {
            return BargainCardAction.NONE;
        }
        if (!isSystemCardMessage(message.getCompleteMsg())) {
            log.warn("【账号{}】忽略非系统小刀卡片: pnmId={}, title={}", message.getXianyuAccountId(), message.getPnmId(), title);
            return BargainCardAction.NONE;
        }
        return WAITING_BARGAIN_TITLES.contains(title) ? BargainCardAction.WAITING_BARGAIN : BargainCardAction.READY_TO_SHIP;
    }

    private String extractBargainCardTitle(String completeMsg) {
        for (JsonNode node : collectJsonNodes(completeMsg)) {
            JsonNode value = node.get("title");
            if (value != null && value.isTextual()) {
                String title = value.asText().trim();
                if (WAITING_BARGAIN_TITLES.contains(title) || READY_TO_SHIP_TITLES.contains(title)) {
                    return title;
                }
            }
        }
        return null;
    }

    private int parseBuyQuantity(ChatMessageData message) {
        if (message.getBuyQuantity() != null && message.getBuyQuantity() > 1) {
            return message.getBuyQuantity();
        }
        String completeMsg = message.getCompleteMsg();
        if (completeMsg == null || completeMsg.isBlank()) {
            return 1;
        }
        for (JsonNode node : collectJsonNodes(completeMsg)) {
            // 闲鱼付款消息中可能的数量字段
            JsonNode qty = node.get("quantity");
            if (qty == null) qty = node.get("buyAmount");
            if (qty == null) qty = node.get("buyNum");
            if (qty == null) qty = node.get("itemCount");
            if (qty != null) {
                int val = qty.asInt(1);
                if (val > 1) {
                    log.info("【账号{}】解析到购买数量: {}", message.getXianyuAccountId(), val);
                    return val;
                }
            }
        }
        return 1;
    }

    private boolean isSystemCardMessage(String completeMsg) {
        JsonNode root = parseJsonNode(completeMsg);
        JsonNode messageNode = root != null ? root.get("1") : null;
        if (messageNode == null || !messageNode.isObject()) {
            return false;
        }
        if (messageNode.path("7").asInt(0) == 1) {
            return true;
        }
        if (messageNode.path("6").path("3").path("4").asInt(0) == 6) {
            return true;
        }
        String bizTag = messageNode.path("10").path("bizTag").asText("");
        return bizTag.contains("SECURITY") || bizTag.contains("taskName") || bizTag.contains("taskId");
    }

    private List<JsonNode> collectJsonNodes(String json) {
        List<JsonNode> nodes = new ArrayList<>();
        JsonNode root = parseJsonNode(json);
        if (root == null) {
            return nodes;
        }
        List<JsonNode> queue = new ArrayList<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < 220) {
            JsonNode node = queue.remove(0);
            visited++;
            if (node == null || node.isNull()) {
                continue;
            }
            nodes.add(node);
            node.elements().forEachRemaining(value -> enqueueJsonNode(queue, value));
        }
        return nodes;
    }

    private void enqueueJsonNode(List<JsonNode> queue, JsonNode value) {
        if (value.isTextual()) {
            JsonNode parsed = parseJsonNode(value.asText());
            if (parsed != null) {
                queue.add(parsed);
            }
            return;
        }
        if (value.isContainerNode()) {
            queue.add(value);
        }
    }

    private JsonNode parseJsonNode(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private XianyuGoodsInfo resolveGoodsInfo(Long accountId, ChatMessageData message) {
        XianyuGoodsInfo goodsInfo = goodsInfoService.ensurePlaceholderGoods(
                accountId,
                message.getXyGoodsId(),
                message.getReminderUrl(),
                null);
        if (goodsInfo == null) {
            log.warn("【账号{}】未找到商品信息: xyGoodsId={}", accountId, message.getXyGoodsId());
            return null;
        }
        return goodsInfo;
    }

    private boolean hasActiveDeliveryRule(Long accountId, String xyGoodsId, String triggerSource, String triggerContent) {
        var goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
            log.info("【账号{}】商品未开启自动发货，仅保留订单记录: xyGoodsId={}", accountId, xyGoodsId);
            return false;
        }
        XianyuGoodsAutoDeliveryConfig deliveryConfig = AutoDeliveryRuleMatcher.select(
                autoDeliveryConfigMapper.findRulesByAccountIdAndGoodsId(accountId, xyGoodsId),
                triggerContent);
        if (deliveryConfig == null) {
            log.info("【账号{}】商品未配置发货规则，仅保留订单记录: xyGoodsId={}", accountId, xyGoodsId);
            return false;
        }
        if (deliveryConfig.getEnabled() != null && deliveryConfig.getEnabled() == 0) {
            log.info("【账号{}】自动发货规则已停用，仅保留订单记录: xyGoodsId={}", accountId, xyGoodsId);
            return false;
        }
        if (TRIGGER_PAYMENT.equals(triggerSource) && deliveryConfig.getTriggerPaymentEnabled() != null
                && deliveryConfig.getTriggerPaymentEnabled() == 0) {
            log.info("【账号{}】付款消息触发已关闭，跳过自动发货: xyGoodsId={}", accountId, xyGoodsId);
            return false;
        }
        if (TRIGGER_BARGAIN.equals(triggerSource) && (deliveryConfig.getTriggerBargainEnabled() == null
                || deliveryConfig.getTriggerBargainEnabled() != 1)) {
            log.info("【账号{}】小刀/讲价卡片触发未开启，跳过自动发货: xyGoodsId={}", accountId, xyGoodsId);
            return false;
        }
        return true;
    }

    private void saveOrderSummary(Long accountId, XianyuGoodsInfo goodsInfo, ChatMessageData message) {
        String orderId = normalizeText(message.getOrderId());
        if (orderId == null) {
            return;
        }
        XianyuOrder existing = xianyuOrderMapper.selectOne(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getXianyuAccountId, accountId)
                .eq(XianyuOrder::getOrderId, orderId));
        if (existing == null) {
            xianyuOrderMapper.insert(buildOrderSummary(accountId, goodsInfo, message, orderId));
            log.info("【账号{}】创建订单概要成功: orderId={}, xyGoodsId={}", accountId, orderId, message.getXyGoodsId());
            orderAutoRefreshService.scheduleRefresh(accountId, orderId);
            return;
        }
        if (refreshOrderSummary(existing, goodsInfo, message)) {
            xianyuOrderMapper.updateById(existing);
            log.info("【账号{}】补全订单概要成功: orderId={}, xyGoodsId={}", accountId, orderId, message.getXyGoodsId());
        }
    }

    private XianyuOrder buildOrderSummary(Long accountId, XianyuGoodsInfo goodsInfo, ChatMessageData message, String orderId) {
        XianyuOrder order = new XianyuOrder();
        order.setXianyuAccountId(accountId);
        order.setOrderId(orderId);
        order.setXyGoodsId(message.getXyGoodsId());
        order.setGoodsTitle(resolveGoodsTitle(goodsInfo));
        order.setBuyerUserId(message.getSenderUserId());
        order.setBuyerUserName(message.getSenderUserName());
        order.setOrderStatus(ORDER_STATUS_PENDING_SHIPMENT);
        order.setOrderStatusText(ORDER_STATUS_PENDING_SHIPMENT_TEXT);
        order.setPnmId(message.getPnmId());
        order.setSId(message.getSId());
        order.setReminderUrl(message.getReminderUrl());
        order.setOrderCreateTime(resolveOrderCreateTime(message));
        order.setOrderPayTime(message.getMessageTime());
        order.setCompleteMsg(message.getCompleteMsg());
        order.setBuyQuantity(parseBuyQuantity(message));
        return order;
    }

    private boolean refreshOrderSummary(XianyuOrder order, XianyuGoodsInfo goodsInfo, ChatMessageData message) {
        boolean changed = false;
        changed |= fillText(order::getXyGoodsId, order::setXyGoodsId, message.getXyGoodsId());
        changed |= fillText(order::getGoodsTitle, order::setGoodsTitle, resolveGoodsTitle(goodsInfo));
        changed |= fillText(order::getBuyerUserId, order::setBuyerUserId, message.getSenderUserId());
        changed |= fillText(order::getBuyerUserName, order::setBuyerUserName, message.getSenderUserName());
        changed |= fillText(order::getPnmId, order::setPnmId, message.getPnmId());
        changed |= fillText(order::getSId, order::setSId, message.getSId());
        changed |= fillText(order::getReminderUrl, order::setReminderUrl, message.getReminderUrl());
        changed |= fillText(order::getCompleteMsg, order::setCompleteMsg, message.getCompleteMsg());
        if (order.getOrderStatus() == null) {
            order.setOrderStatus(ORDER_STATUS_PENDING_SHIPMENT);
            order.setOrderStatusText(ORDER_STATUS_PENDING_SHIPMENT_TEXT);
            changed = true;
        }
        if (order.getOrderCreateTime() == null) {
            order.setOrderCreateTime(resolveOrderCreateTime(message));
            changed = true;
        }
        if (order.getOrderPayTime() == null && message.getMessageTime() != null) {
            order.setOrderPayTime(message.getMessageTime());
            changed = true;
        }
        return changed;
    }

    private Long createOrderRecord(Long accountId, Long xianyuGoodsId, ChatMessageData message, String triggerSource) {
        if (message.getOrderId() != null && !message.getOrderId().trim().isEmpty()) {
            XianyuGoodsOrder existing = orderMapper.selectLatestByOrderId(accountId, message.getXyGoodsId(), message.getOrderId());
            if (existing != null) {
                log.info("【账号{}】订单已创建过发货记录，跳过重复触发: orderId={}, recordId={}",
                        accountId, message.getOrderId(), existing.getId());
                return null;
            }
        }
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXianyuGoodsId(xianyuGoodsId);
        record.setXyGoodsId(message.getXyGoodsId());
        record.setPnmId(message.getPnmId());
        record.setBuyerUserId(message.getSenderUserId());
        record.setBuyerUserName(message.getSenderUserName());
        record.setSid(message.getSId());
        record.setOrderId(message.getOrderId());
        record.setTriggerSource(triggerSource);
        record.setTriggerContent(message.getMsgContent());
        record.setContent(null);
        record.setState(0);
        record.setConfirmState(0);
        record.setCreateTime(DateTimeUtils.currentShanghaiTime());

        try {
            int result = orderMapper.insert(record);
            if (result > 0) {
                log.info("【账号{}】✅ 创建订单记录成功: recordId={}, orderId={}", accountId, record.getId(), message.getOrderId());
                return record.getId();
            } else {
                log.error("【账号{}】❌ 创建订单记录失败: pnmId={}, orderId={}", accountId, message.getPnmId(), message.getOrderId());
                return null;
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                log.info("【账号{}】消息已处理过，跳过: pnmId={}, xyGoodsId={}", accountId, message.getPnmId(), message.getXyGoodsId());
                return null;
            }
            throw new RuntimeException("创建订单记录失败", e);
        }
    }

    private String resolveGoodsTitle(XianyuGoodsInfo goodsInfo) {
        return goodsInfo != null ? normalizeText(goodsInfo.getTitle()) : null;
    }

    private Long resolveOrderCreateTime(ChatMessageData message) {
        return message.getMessageTime() != null ? message.getMessageTime() : System.currentTimeMillis();
    }

    private boolean fillText(java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter,
                             String value) {
        String normalized = normalizeText(value);
        if (normalized == null || normalizeText(getter.get()) != null) {
            return false;
        }
        setter.accept(normalized);
        return true;
    }

    private String normalizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum BargainCardAction {
        NONE,
        WAITING_BARGAIN,
        READY_TO_SHIP
    }
}
