package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoReplyRecord;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.entity.XianyuKamiUsageRecord;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.config.WebSocketConfig;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.ManualReturnApiDeliveryReqDTO;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.ApiDeliveryService;
import com.feijimiao.xianyuassistant.service.AutoDeliveryRuleMatcher;
import com.feijimiao.xianyuassistant.service.DeliveryMessageSettingService;
import com.feijimiao.xianyuassistant.service.EmailNotifyService;
import com.feijimiao.xianyuassistant.service.KamiConfigService;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryResult;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自动发货服务实现类
 */
@Slf4j
@Service
public class AutoDeliveryServiceImpl implements AutoDeliveryService {

    private static final int MAX_DELIVERY_DELAY_SECONDS = 86400;
    private static final int DELIVERY_MODE_API = 4;
    private static final String MULTI_QUANTITY_SEND_MODE_KEY = "delivery_multi_quantity_send_mode";
    private static final String SEND_MODE_SEPARATE = "separate";
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;
    
    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    
    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private XianyuOrderMapper xianyuOrderMapper;
    
    @Autowired
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;
    
    @Lazy
    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private WebSocketConfig webSocketConfig;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.SentMessageSaveService sentMessageSaveService;

    @Autowired
    private KamiConfigService kamiConfigService;

    @Autowired
    private EmailNotifyService emailNotifyService;

    @Autowired
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeliveryMessageSettingService deliveryMessageSettingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationContentBuilder notificationContentBuilder;

    @Autowired
    private ApiDeliveryService apiDeliveryService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private SysSettingService sysSettingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public XianyuGoodsConfig getGoodsConfig(Long accountId, String xyGoodsId) {
        return goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
    }
    
    @Override
    public XianyuGoodsAutoDeliveryConfig getAutoDeliveryConfig(Long accountId, String xyGoodsId) {
        return autoDeliveryConfigMapper.findByAccountIdAndGoodsId(accountId, xyGoodsId);
    }
    
    @Override
    public void saveOrUpdateGoodsConfig(XianyuGoodsConfig config) {
        XianyuGoodsConfig existing = goodsConfigMapper.selectByAccountAndGoodsId(
                config.getXianyuAccountId(), config.getXyGoodsId());
        
        if (existing == null) {
            goodsConfigMapper.insert(config);
        } else {
            config.setId(existing.getId());
            goodsConfigMapper.update(config);
        }
    }
    
    @Override
    public void saveOrUpdateAutoDeliveryConfig(XianyuGoodsAutoDeliveryConfig config) {
        XianyuGoodsAutoDeliveryConfig existing = autoDeliveryConfigMapper.findByAccountIdAndGoodsId(
                config.getXianyuAccountId(), config.getXyGoodsId());
        
        if (existing == null) {
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setId(existing.getId());
            autoDeliveryConfigMapper.updateById(config);
        }
    }
    
    @Override
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, String content, Integer state) {
        // 使用新的重载方法，传入 null 作为 pnmId 和 orderId
        recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, state, null, null);
    }
    
    /**
     * 记录自动发货（带 pnmId 和 orderId）
     */
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, 
                                   String content, Integer state, String pnmId, String orderId) {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXyGoodsId(xyGoodsId);
        record.setBuyerUserId(buyerUserId);
        record.setBuyerUserName(buyerUserName);
        record.setContent(content);
        record.setState(state);
        record.setPnmId(!isBlank(pnmId) ? pnmId : buildManualPnmId(orderId, null));
        record.setOrderId(orderId != null ? orderId : "");
        record.setTriggerSource("manual");
        record.setTriggerContent("手动记录");
        record.setConfirmState(0);
        record.setCreateTime(DateTimeUtils.currentShanghaiTime());
        
        orderMapper.insert(record);
    }
    
    /**
     * 处理自动发货（带买家用户ID和用户名）
     */
    @Override
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName) {
        // 调用重载方法，传入 null 作为 orderId
        handleAutoDelivery(accountId, xyGoodsId, sId, buyerUserId, buyerUserName, null);
    }
    
    /**
     * 处理自动发货（带订单ID）
     */
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName, String orderId) {
        try {
            log.info("【账号{}】处理自动发货: xyGoodsId={}, sId={}, buyerUserId={}, buyerUserName={}, orderId={}", 
                    accountId, xyGoodsId, sId, buyerUserId, buyerUserName, orderId);

            XianyuGoodsConfig goodsConfig = getGoodsConfig(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }

            if (!isBlank(orderId) && orderMapper.selectLatestByOrderId(accountId, xyGoodsId, orderId) != null) {
                log.info("【账号{}】订单已创建过发货记录，跳过重复触发: orderId={}", accountId, orderId);
                return;
            }

            XianyuGoodsOrder record = new XianyuGoodsOrder();
            record.setXianyuAccountId(accountId);
            record.setXyGoodsId(xyGoodsId);
            record.setPnmId(buildManualPnmId(orderId, sId));
            record.setOrderId(orderId != null ? orderId : "");
            record.setBuyerUserId(buyerUserId);
            record.setBuyerUserName(buyerUserName);
            record.setSid(sId);
            record.setTriggerSource("manual");
            record.setTriggerContent("手动触发自动发货");
            record.setState(0);
            record.setConfirmState(0);
            record.setCreateTime(DateTimeUtils.currentShanghaiTime());
            orderMapper.insert(record);

            executeDelivery(record.getId(), accountId, xyGoodsId, sId, orderId, buyerUserName, true);
        } catch (Exception e) {
            log.error("【账号{}】自动发货异常: xyGoodsId={}", accountId, xyGoodsId, e);
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
        }
    }
    
    @Override
    public void handleAutoReply(Long accountId, String xyGoodsId, String sId, String buyerMessage) {
        log.info("【账号{}】自动回复功能已移除: xyGoodsId={}", accountId, xyGoodsId);
    }
    
    private void recordAutoReply(Long accountId, String xyGoodsId, String buyerMessage, 
                                  String replyContent, String matchedKeyword, Integer state) {
        try {
            XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
            record.setXianyuAccountId(accountId);
            record.setXyGoodsId(xyGoodsId);
            record.setBuyerMessage(buyerMessage);
            record.setReplyContent(replyContent);
            record.setMatchedKeyword(matchedKeyword);
            record.setState(state);
            
            autoReplyRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("【账号{}】记录自动回复失败", accountId, e);
        }
    }
    
    @Override
    public com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO getAutoDeliveryRecords(
            com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordReqDTO reqDTO) {
        
        Long accountId = reqDTO.getXianyuAccountId();
        String xyGoodsId = reqDTO.getXyGoodsId();
        Integer state = reqDTO.getState();
        int pageNum = reqDTO.getPageNum() != null ? reqDTO.getPageNum() : 1;
        int pageSize = reqDTO.getPageSize() != null ? reqDTO.getPageSize() : 20;
        
        // 计算偏移量
        int offset = (pageNum - 1) * pageSize;
        
        // 查询记录
        List<XianyuGoodsOrder> records = orderMapper.selectByAccountIdWithPage(
                accountId, xyGoodsId, state, pageSize, offset);
        
        // 统计总数
        long total = orderMapper.countByAccountId(accountId, xyGoodsId, state);
        
        // 转换为DTO
        List<com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO> recordDTOs = new ArrayList<>();
        for (XianyuGoodsOrder record : records) {
            com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO dto = 
                    new com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO();
            dto.setId(record.getId());
            dto.setXianyuAccountId(record.getXianyuAccountId());
            dto.setXyGoodsId(record.getXyGoodsId());
            dto.setGoodsTitle(record.getGoodsTitle());
            dto.setBuyerUserName(record.getBuyerUserName());
            dto.setContent(record.getContent());
            dto.setDeliveryMode(record.getDeliveryMode());
            dto.setRuleName(record.getRuleName());
            dto.setTriggerSource(record.getTriggerSource());
            dto.setTriggerContent(record.getTriggerContent());
            dto.setState(record.getState());
            dto.setFailReason(record.getFailReason());
            dto.setConfirmState(record.getConfirmState());
            dto.setOrderId(record.getOrderId());
            dto.setExternalAllocationId(record.getExternalAllocationId());
            dto.setExternalConfirmState(record.getExternalConfirmState());
            dto.setExternalReturnState(record.getExternalReturnState());
            dto.setExternalReturnReason(record.getExternalReturnReason());
            dto.setCreateTime(record.getCreateTime());
            recordDTOs.add(dto);
        }
        
        // 构建响应
        com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO respDTO = 
                new com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO();
        respDTO.setRecords(recordDTOs);
        respDTO.setTotal(total);
        respDTO.setPageNum(pageNum);
        respDTO.setPageSize(pageSize);
        
        return respDTO;
    }

    @Override
    public com.feijimiao.xianyuassistant.common.ResultObject<String> triggerAutoDelivery(
            com.feijimiao.xianyuassistant.controller.dto.TriggerAutoDeliveryReqDTO reqDTO) {
        try {
            Long accountId = reqDTO.getXianyuAccountId();
            String xyGoodsId = reqDTO.getXyGoodsId();
            String orderId = reqDTO.getOrderId();
            Boolean needHumanLikeDelay = reqDTO.getNeedHumanLikeDelay() != null ? reqDTO.getNeedHumanLikeDelay() : false;

            log.info("【账号{}】触发自动发货: xyGoodsId={}, orderId={}, needHumanLikeDelay={}", 
                    accountId, xyGoodsId, orderId, needHumanLikeDelay);

            XianyuGoodsOrder record = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (record == null) {
                log.warn("【账号{}】发货记录不存在: orderId={}", accountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货记录不存在");
            }

            String pnmId = record.getPnmId();
            if (pnmId == null || pnmId.isEmpty()) {
                log.warn("【账号{}】发货记录没有pnmId: orderId={}", accountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货记录没有pnmId");
            }

            Long recordId = record.getId();
            String sId = record.getSid() != null ? record.getSid() : record.getBuyerUserId() + "@goofish";
            String buyerUserName = record.getBuyerUserName();

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("商品未开启自动发货");
            }

            executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, needHumanLikeDelay);

            XianyuGoodsOrder updatedRecord = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (updatedRecord != null && updatedRecord.getState() == 1) {
                return com.feijimiao.xianyuassistant.common.ResultObject.success("触发自动发货成功");
            } else {
                String failReason = updatedRecord != null ? updatedRecord.getFailReason() : "未知错误";
                return com.feijimiao.xianyuassistant.common.ResultObject.failed(failReason != null ? failReason : "发货失败");
            }

        } catch (Exception e) {
            log.error("【账号{}】触发自动发货失败: xyGoodsId={}, orderId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getOrderId(), e);
            return com.feijimiao.xianyuassistant.common.ResultObject.failed("触发自动发货失败: " + e.getMessage());
        }
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId, String orderId, String buyerUserName, boolean needHumanLikeDelay) {
        executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, needHumanLikeDelay, 1);
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId, String orderId, String buyerUserName, boolean needHumanLikeDelay, int buyQuantity) {
        try {
            log.info("【账号{}】开始执行自动发货: recordId={}, xyGoodsId={}, orderId={}", accountId, recordId, xyGoodsId, orderId);

            XianyuGoodsOrder record = orderMapper.selectById(recordId);
            XianyuGoodsAutoDeliveryConfig deliveryConfig = selectDeliveryConfig(accountId, xyGoodsId, record);
            if (deliveryConfig == null) {
                log.warn("【账号{}】商品未配置发货模式: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(recordId, -1, null, "未匹配到可用发货规则");
                return;
            }
            if (record == null) {
                log.warn("【账号{}】发货记录不存在: recordId={}", accountId, recordId);
                updateRecordState(recordId, -1, null, "发货记录不存在");
                return;
            }

            String ruleName = resolveRuleName(deliveryConfig);
            orderMapper.updateDeliverySnapshot(recordId, deliveryConfig.getDeliveryMode(), ruleName, buildDeliverySnapshot(deliveryConfig));
            String blockedReason = validateDeliveryRule(deliveryConfig);
            if (blockedReason != null) {
                updateRecordState(recordId, -1, null, blockedReason);
                notifyDeliveryFail(accountId, xyGoodsId, orderId, ruleName, blockedReason);
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, blockedReason);
                return;
            }

            String closedReason = resolveClosedOrderReason(accountId, orderId);
            if (closedReason != null) {
                updateRecordState(recordId, -1, null, closedReason);
                return;
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            int requestedQuantity = resolveRequestedQuantity(buyQuantity);
            boolean separateSendMode = isSeparateMultiQuantitySendMode();
            DeliveryContent deliveryContent;
            if (deliveryMode == 2 || deliveryMode == DELIVERY_MODE_API) {
                if (!applyDeliveryDelay(deliveryConfig, accountId, xyGoodsId, orderId)) {
                    updateRecordState(recordId, -1, null, "延时发货被中断");
                    returnApiDeliveryIfNeeded(deliveryMode, deliveryConfig, recordId, null, "延时发货被中断");
                    return;
                }
                closedReason = resolveClosedOrderReason(accountId, orderId);
                if (closedReason != null) {
                    updateRecordState(recordId, -1, null, closedReason);
                    return;
                }
                deliveryContent = resolveDeliveryContent(deliveryMode, deliveryConfig, recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, requestedQuantity, separateSendMode);
            } else {
                deliveryContent = resolveDeliveryContent(deliveryMode, deliveryConfig, recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, requestedQuantity, separateSendMode);
                if (deliveryContent != null && !applyDeliveryDelay(deliveryConfig, accountId, xyGoodsId, orderId)) {
                    updateRecordState(recordId, -1, null, "延时发货被中断");
                    returnApiDeliveryIfNeeded(deliveryMode, deliveryConfig, recordId, deliveryContent, "延时发货被中断");
                    return;
                }
            }
            if (deliveryContent == null) {
                return;
            }
            String content = deliveryContent.getMessageContent();

            if (needHumanLikeDelay) {
                log.debug("【账号{}】模拟人工操作延迟...", accountId);
                HumanLikeDelayUtils.mediumDelay();
                HumanLikeDelayUtils.thinkingDelay();
                HumanLikeDelayUtils.typingDelay(content.length());
            }

            if (abortDeliveryForClosedOrderIfNeeded(accountId, orderId, deliveryMode, deliveryConfig, recordId, deliveryContent)) {
                return;
            }

            String cid = sId.replace("@goofish", "");
            String toId = cid;

            sendDeliveryImages(accountId, xyGoodsId, cid, toId, deliveryConfig, needHumanLikeDelay);

            log.info("【账号{}】准备发送发货文本: content长度={}, deliveryMode={}, messageCount={}",
                    accountId, content.length(), deliveryMode, deliveryContent.getMessageContents().size());
            boolean success = sendDeliveryMessagesWithRetry(accountId, cid, toId, deliveryContent);
            if (success) {
                deliveryContent.markMainDeliveryComplete();
                saveMainDeliveryReplies(accountId, cid, toId, xyGoodsId, deliveryContent);
                success = sendPostDeliveryTextIfNeeded(accountId, cid, toId, xyGoodsId, deliveryConfig);
            }
            record = orderMapper.selectById(recordId);

            if (success) {
                log.info("【账号{}】✅ 自动发货成功: recordId={}, xyGoodsId={}, deliveryMode={}", accountId, recordId, xyGoodsId, deliveryMode);
                updateRecordState(recordId, 1, content, null);
                logAutoDeliveryResult(accountId, recordId, xyGoodsId, orderId, sId, buyerUserName, deliveryConfig, deliveryMode,
                        true, "自动发货成功", null, content);
                int deliveredQuantity = deliveryContent.getDeliveredQuantity();
                autoDeliveryConfigMapper.markDeliverySuccessBatch(deliveryConfig.getId(), deliveredQuantity);
                deliveryMessageSettingService.sendKamiSingleMessage(accountId, cid, toId, deliveryContent.getRawKamiContent(), xyGoodsId);
                confirmApiDeliveryIfNeeded(deliveryMode, deliveryConfig, record, deliveryContent);
                notifyDeliverySuccess(accountId, xyGoodsId, orderId, deliveryConfig);
                notifyStockWarningIfNeeded(accountId, xyGoodsId, deliveryConfig, deliveredQuantity);

                if (deliveryConfig.getAutoConfirmShipment() != null && deliveryConfig.getAutoConfirmShipment() == 1) {
                    log.info("【账号{}】检测到自动确认发货开关已开启，准备自动确认发货: orderId={}", accountId, orderId);
                    executeAutoConfirmShipment(accountId, orderId);
                }
            } else {
                String failReason = isMainDeliveryComplete(deliveryContent) ? "发货后文本发送失败" : "消息发送失败";
                log.error("【账号{}】❌ 自动发货失败(服务端拒绝): recordId={}, xyGoodsId={}, reason={}", accountId, recordId, xyGoodsId, failReason);
                updateRecordState(recordId, -1, content, failReason);
                logAutoDeliveryResult(accountId, recordId, xyGoodsId, orderId, sId, buyerUserName, deliveryConfig, deliveryMode,
                        false, "自动发货失败：" + failReason, failReason, content);
                if (!isMainDeliveryComplete(deliveryContent)) {
                    returnApiDeliveryIfNeeded(deliveryMode, deliveryConfig, recordId, deliveryContent, "消息发送最终失败");
                }
                notifyDeliveryFail(accountId, xyGoodsId, orderId, ruleName, failReason);
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
            }

        } catch (Exception e) {
            log.error("【账号{}】执行自动发货异常: recordId={}, xyGoodsId={}", accountId, recordId, xyGoodsId, e);
            String failReason = "发货异常: " + e.getMessage();
            updateRecordState(recordId, -1, null, failReason);
            XianyuGoodsAutoDeliveryConfig deliveryConfig = null;
            XianyuGoodsOrder record = orderMapper.selectById(recordId);
            if (record != null) {
                deliveryConfig = selectDeliveryConfig(accountId, xyGoodsId, record);
            }
            logAutoDeliveryResult(accountId, recordId, xyGoodsId, orderId, sId, buyerUserName, deliveryConfig,
                    record != null && record.getDeliveryMode() != null ? record.getDeliveryMode() : null,
                    false, "自动发货异常", failReason, null);
            if (record != null && record.getDeliveryMode() != null && record.getDeliveryMode() == DELIVERY_MODE_API
                    && !isBlank(record.getExternalAllocationId())
                    && (record.getExternalReturnState() == null || record.getExternalReturnState() != 1)) {
                XianyuGoodsAutoDeliveryConfig config = deliveryConfig;
                if (config != null && isApiMode(config)) {
                    returnApiDelivery(config, record, record.getExternalAllocationId(), failReason);
                }
            }
            notifyDeliveryFail(accountId, xyGoodsId, orderId, record != null ? record.getRuleName() : null, failReason);
            emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
        }
    }

    @Override
    public ResultObject<String> manualReturnApiDelivery(ManualReturnApiDeliveryReqDTO reqDTO) {
        Long accountId = reqDTO.getXianyuAccountId();
        String xyGoodsId = reqDTO.getXyGoodsId();
        Long recordId = reqDTO.getRecordId();

        XianyuGoodsOrder record = orderMapper.selectByIdAndAccountAndGoods(recordId, accountId, xyGoodsId);
        if (record == null) {
            log.warn("【账号{}】手动回库失败，发货记录不存在: recordId={}, xyGoodsId={}", accountId, recordId, xyGoodsId);
            return ResultObject.failed("发货记录不存在");
        }
        if (!Objects.equals(record.getDeliveryMode(), DELIVERY_MODE_API)) {
            return ResultObject.failed("该记录不是API发货，无需回库");
        }
        if (isBlank(record.getExternalAllocationId())) {
            return ResultObject.failed("该记录没有外部分配ID，无法回库");
        }
        if (Objects.equals(record.getExternalReturnState(), 1)) {
            return ResultObject.failed("该记录已回库，无需重复操作");
        }

        XianyuGoodsAutoDeliveryConfig config = selectDeliveryConfig(accountId, xyGoodsId, record);
        if (config == null) {
            return ResultObject.failed("未找到该记录对应的自动发货配置");
        }
        if (!Objects.equals(config.getDeliveryMode(), DELIVERY_MODE_API)) {
            return ResultObject.failed("当前商品不是API发货配置");
        }

        String reason = "手动回库";
        returnApiDelivery(config, record, record.getExternalAllocationId(), reason);

        XianyuGoodsOrder updatedRecord = orderMapper.selectById(recordId);
        if (updatedRecord != null && Objects.equals(updatedRecord.getExternalReturnState(), 1)) {
            return ResultObject.success("手动回库成功");
        }
        String failReason = updatedRecord != null ? updatedRecord.getExternalReturnReason() : null;
        return ResultObject.failed(isBlank(failReason) ? "手动回库失败" : failReason);
    }

    private String validateDeliveryRule(XianyuGoodsAutoDeliveryConfig deliveryConfig) {
        if (deliveryConfig.getEnabled() != null && deliveryConfig.getEnabled() == 0) {
            return "自动发货规则已停用";
        }
        if (deliveryConfig.getStock() != null && deliveryConfig.getStock() == 0) {
            return "本地库存不足";
        }
        if (isApiMode(deliveryConfig) && isBlank(deliveryConfig.getApiAllocateUrl())) {
            return "API发货模式未配置分配接口URL";
        }
        return null;
    }

    private XianyuGoodsAutoDeliveryConfig selectDeliveryConfig(Long accountId, String xyGoodsId, XianyuGoodsOrder record) {
        List<XianyuGoodsAutoDeliveryConfig> rules = autoDeliveryConfigMapper.findRulesByAccountIdAndGoodsId(accountId, xyGoodsId);
        XianyuGoodsAutoDeliveryConfig recordedRule = findRuleByRecordedRuleName(rules, record);
        if (recordedRule != null) {
            log.info("【账号{}】自动发货使用记录规则: xyGoodsId={}, ruleId={}, ruleName={}",
                    accountId, xyGoodsId, recordedRule.getId(), resolveRuleName(recordedRule));
            return recordedRule;
        }
        String triggerContent = record != null ? record.getTriggerContent() : null;
        XianyuGoodsAutoDeliveryConfig selected = AutoDeliveryRuleMatcher.select(rules, triggerContent);
        if (selected != null) {
            log.info("【账号{}】自动发货匹配规则: xyGoodsId={}, ruleId={}, ruleName={}",
                    accountId, xyGoodsId, selected.getId(), resolveRuleName(selected));
        }
        return selected;
    }

    private XianyuGoodsAutoDeliveryConfig findRuleByRecordedRuleName(List<XianyuGoodsAutoDeliveryConfig> rules,
                                                                     XianyuGoodsOrder record) {
        if (rules == null || rules.isEmpty() || record == null || isBlank(record.getRuleName())) {
            return null;
        }
        String recordedRuleName = record.getRuleName().trim();
        for (XianyuGoodsAutoDeliveryConfig rule : rules) {
            if (rule == null || isBlank(rule.getRuleName())) {
                continue;
            }
            if (recordedRuleName.equalsIgnoreCase(rule.getRuleName().trim())) {
                return rule;
            }
        }
        return null;
    }

    private String resolveRuleName(XianyuGoodsAutoDeliveryConfig deliveryConfig) {
        String ruleName = deliveryConfig.getRuleName();
        if (ruleName != null && !ruleName.trim().isEmpty()) {
            return ruleName.trim();
        }
        return "商品规则-" + deliveryConfig.getXyGoodsId();
    }

    private String buildDeliverySnapshot(XianyuGoodsAutoDeliveryConfig config) {
        List<String> parts = new ArrayList<>();
        parts.add("mode=" + (config.getDeliveryMode() != null ? config.getDeliveryMode() : 1));
        parts.add("rule=" + resolveRuleName(config));
        parts.add("stock=" + (config.getStock() != null ? config.getStock() : -1));
        parts.add("enabled=" + (config.getEnabled() != null ? config.getEnabled() : 1));
        parts.add("delay=" + resolveDeliveryDelaySeconds(config));
        parts.add("triggerPayment=" + (config.getTriggerPaymentEnabled() != null ? config.getTriggerPaymentEnabled() : 1));
        parts.add("triggerBargain=" + (config.getTriggerBargainEnabled() != null ? config.getTriggerBargainEnabled() : 0));
        parts.add("postText=" + (!isBlank(config.getPostDeliveryText()) ? 1 : 0));
        if (isApiMode(config)) {
            parts.add("apiAllocate=" + safeText(config.getApiAllocateUrl()));
            parts.add("apiConfirm=" + safeText(config.getApiConfirmUrl()));
            parts.add("apiReturn=" + safeText(config.getApiReturnUrl()));
            parts.add("apiProtocol=delivery-v1");
        }
        return parts.stream().collect(Collectors.joining(";"));
    }

    private boolean applyDeliveryDelay(XianyuGoodsAutoDeliveryConfig deliveryConfig, Long accountId, String xyGoodsId, String orderId) {
        int delaySeconds = resolveDeliveryDelaySeconds(deliveryConfig);
        if (delaySeconds <= 0) {
            return true;
        }
        log.info("【账号{}】延时发货等待: xyGoodsId={}, orderId={}, delay={}秒", accountId, xyGoodsId, orderId, delaySeconds);
        try {
            Thread.sleep(delaySeconds * 1000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("【账号{}】延时发货等待被中断: xyGoodsId={}, orderId={}", accountId, xyGoodsId, orderId);
            return false;
        }
    }

    private int resolveDeliveryDelaySeconds(XianyuGoodsAutoDeliveryConfig deliveryConfig) {
        Integer delaySeconds = deliveryConfig != null ? deliveryConfig.getDeliveryDelaySeconds() : null;
        if (delaySeconds == null || delaySeconds <= 0) {
            return 0;
        }
        return Math.min(delaySeconds, MAX_DELIVERY_DELAY_SECONDS);
    }

    private int resolveRequestedQuantity(int buyQuantity) {
        return Math.max(1, buyQuantity);
    }

    private boolean isSeparateMultiQuantitySendMode() {
        String value = sysSettingService != null ? sysSettingService.getSettingValue(MULTI_QUANTITY_SEND_MODE_KEY) : null;
        return SEND_MODE_SEPARATE.equalsIgnoreCase(value != null ? value.trim() : null);
    }

    private void logAutoDeliveryResult(Long accountId, Long recordId, String xyGoodsId, String orderId, String sId,
                                       String buyerUserName, XianyuGoodsAutoDeliveryConfig config, Integer deliveryMode,
                                       boolean success, String desc, String errorMessage, String content) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("recordId", recordId);
            params.put("xyGoodsId", safeText(xyGoodsId));
            params.put("orderId", safeText(orderId));
            params.put("sId", safeText(sId));
            params.put("buyerUserName", safeText(buyerUserName));
            params.put("ruleId", config != null ? config.getId() : null);
            params.put("ruleName", config != null ? resolveRuleName(config) : "");
            params.put("deliveryMode", deliveryMode);
            params.put("deliveryModeLabel", config != null ? resolveDeliveryModeLabel(config) : "");
            params.put("contentLength", content != null ? content.length() : 0);
            operationLogService.log(
                    accountId,
                    OperationConstants.Type.SEND,
                    OperationConstants.Module.AUTO_DELIVERY,
                    desc,
                    success ? OperationConstants.Status.SUCCESS : OperationConstants.Status.FAIL,
                    OperationConstants.TargetType.ORDER,
                    recordId != null ? String.valueOf(recordId) : safeText(orderId),
                    objectMapper.writeValueAsString(params),
                    null,
                    errorMessage,
                    null);
        } catch (Exception e) {
            log.warn("【账号{}】记录自动发货结果操作日志失败: recordId={}, xyGoodsId={}", accountId, recordId, xyGoodsId, e);
        }
    }

    private void notifyDeliverySuccess(Long accountId, String xyGoodsId, String orderId, XianyuGoodsAutoDeliveryConfig config) {
        String title = "【闲鱼助手】自动发货成功";
        String content = notificationContentBuilder.eventContent(
                accountId,
                "订单已自动发货成功",
                buildGoodsText(xyGoodsId)
                        + "\n订单ID：" + safeText(orderId)
                        + "\n规则：" + resolveRuleName(config)
                        + "\n发货模式：" + resolveDeliveryModeLabel(config),
                "如已开启自动确认发货，系统会继续确认交付；否则可在发货记录中复核。"
        );
        notificationService.notifyEvent(NotificationService.EVENT_DELIVERY_SUCCESS, title, content);
    }

    private void notifyDeliveryFail(Long accountId, String xyGoodsId, String orderId, String ruleName, String reason) {
        String title = "【闲鱼助手】自动发货失败";
        String content = notificationContentBuilder.eventContent(
                accountId,
                "自动发货执行失败",
                buildGoodsText(xyGoodsId)
                        + "\n订单ID：" + safeText(orderId)
                        + "\n规则：" + safeText(ruleName)
                        + "\n失败原因：" + safeText(reason),
                "请检查对应商品规则、库存、连接状态和验证码状态，再决定是否手动补发。"
        );
        notificationService.notifyEvent(NotificationService.EVENT_DELIVERY_FAIL, title, content);
    }

    private void notifyStockWarningIfNeeded(Long accountId, String xyGoodsId, XianyuGoodsAutoDeliveryConfig config,
                                            int deliveredQuantity) {
        Integer stock = config.getStock();
        Integer threshold = config.getStockWarnThreshold();
        if (stock == null || threshold == null || threshold <= 0 || stock < 0) {
            return;
        }
        int remaining = Math.max(0, stock - Math.max(1, deliveredQuantity));
        if (remaining <= threshold) {
            notificationService.notifyEvent(
                    NotificationService.EVENT_STOCK_WARNING,
                    "【闲鱼助手】自动发货库存预警",
                    notificationContentBuilder.eventContent(
                            accountId,
                            "自动发货库存达到预警阈值",
                            buildGoodsText(xyGoodsId)
                                    + "\n规则：" + resolveRuleName(config)
                                    + "\n剩余库存：" + remaining
                                    + "\n预警阈值：" + threshold,
                            "请及时补库或关闭该规则，避免继续成交后无法发货。"
                    )
            );
        }
    }

    private String resolveDeliveryModeLabel(XianyuGoodsAutoDeliveryConfig config) {
        int mode = config != null && config.getDeliveryMode() != null ? config.getDeliveryMode() : 1;
        return switch (mode) {
            case 2 -> "卡密";
            case 3 -> "自定义";
            case 4 -> "API";
            default -> "文本";
        };
    }

    private String buildGoodsText(String xyGoodsId) {
        String title = resolveGoodsTitle(xyGoodsId);
        if (title == null || title.isEmpty()) {
            return "商品ID: " + safeText(xyGoodsId);
        }
        return "商品: " + title + "（ID: " + safeText(xyGoodsId) + "）";
    }

    private String resolveGoodsTitle(String xyGoodsId) {
        if (xyGoodsId == null || xyGoodsId.isEmpty()) {
            return "";
        }
        try {
            QueryWrapper<XianyuGoodsInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("xy_good_id", xyGoodsId);
            queryWrapper.last("LIMIT 1");
            XianyuGoodsInfo goodsInfo = goodsInfoMapper.selectOne(queryWrapper);
            return goodsInfo != null ? goodsInfo.getTitle() : "";
        } catch (Exception e) {
            log.debug("查询商品名称失败: xyGoodsId={}", xyGoodsId, e);
            return "";
        }
    }

    private String safeText(String text) {
        return text == null || text.isEmpty() ? "-" : text;
    }

    private boolean isApiMode(XianyuGoodsAutoDeliveryConfig config) {
        return config != null && config.getDeliveryMode() != null && config.getDeliveryMode() == DELIVERY_MODE_API;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private DeliveryContent resolveDeliveryContent(int deliveryMode, XianyuGoodsAutoDeliveryConfig deliveryConfig,
                                                   Long recordId, Long accountId, String xyGoodsId, String sId,
                                                   String orderId, String buyerUserName, int buyQuantity,
                                                   boolean separateSendMode) {
        if (deliveryMode == 1) {
            if (deliveryConfig.getAutoDeliveryContent() == null || deliveryConfig.getAutoDeliveryContent().isEmpty()) {
                log.warn("【账号{}】自动发货模式下未配置发货内容: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(recordId, -1, null, "未配置发货内容");
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "未配置发货内容");
                return null;
            }
            log.info("【账号{}】文本发货模式", accountId);
            return repeatTextDeliveryContent(deliveryConfig.getAutoDeliveryContent(), buyQuantity, separateSendMode);
        } else if (deliveryMode == 2) {
            DeliveryContent content = acquireKamiContent(deliveryConfig.getKamiConfigIds(), deliveryConfig.getKamiDeliveryTemplate(), orderId, accountId, xyGoodsId, sId, buyerUserName, buyQuantity, separateSendMode);
            if (content == null) {
                log.warn("【账号{}】卡密发货模式下无可用卡密: xyGoodsId={}, kamiConfigIds={}", accountId, xyGoodsId, deliveryConfig.getKamiConfigIds());
                updateRecordState(recordId, -1, null, "卡密库存不足，无可用卡密");
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "卡密库存不足，无可用卡密");
                return null;
            }
            log.info("【账号{}】卡密发货模式: content长度={}", accountId, content.getMessageContent().length());
            return content;
        } else if (deliveryMode == 3) {
            log.info("【账号{}】自定义发货模式，不自动发送消息: xyGoodsId={}", accountId, xyGoodsId);
            updateRecordState(recordId, 1, "自定义发货-请通过API处理", null);
            return null;
        } else if (deliveryMode == DELIVERY_MODE_API) {
            return acquireApiDeliveryContent(deliveryConfig, recordId, accountId, xyGoodsId, sId, orderId,
                    buyerUserName, buyQuantity, separateSendMode);
        } else {
            log.warn("【账号{}】未知的发货模式: deliveryMode={}", accountId, deliveryMode);
            updateRecordState(recordId, -1, null, "未知的发货模式: " + deliveryMode);
            emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "未知的发货模式: " + deliveryMode);
            return null;
        }
    }

    private DeliveryContent repeatTextDeliveryContent(String textContent, int quantity, boolean separateSendMode) {
        int requestedQuantity = resolveRequestedQuantity(quantity);
        if (separateSendMode) {
            return new DeliveryContent(repeatContent(textContent, requestedQuantity), null, requestedQuantity);
        }
        return new DeliveryContent(String.join("\n", repeatContent(textContent, requestedQuantity)), null, requestedQuantity);
    }

    private List<String> repeatContent(String content, int quantity) {
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            contents.add(content);
        }
        return contents;
    }

    private void sendDeliveryImages(Long accountId, String xyGoodsId, String cid, String toId,
                                    XianyuGoodsAutoDeliveryConfig deliveryConfig, boolean needHumanLikeDelay) {
        String imageUrlStr = deliveryConfig.getAutoDeliveryImageUrl();
        if (imageUrlStr == null || imageUrlStr.trim().isEmpty()) {
            return;
        }
        String[] imageUrls = imageUrlStr.split(",");
        for (int i = 0; i < imageUrls.length; i++) {
            try {
                String url = imageUrls[i].trim();
                if (url.isEmpty()) continue;
                if (i > 0) {
                    if (needHumanLikeDelay) {
                        HumanLikeDelayUtils.thinkingDelay();
                    } else {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
                boolean imgSuccess = webSocketService.sendImageMessage(accountId, cid, toId, url, 800, 800);
                if (imgSuccess) {
                    log.info("【账号{}】自动发货图片[{}/{}]发送成功: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                    sentMessageSaveService.saveManualImageReply(accountId, cid, toId, url, xyGoodsId);
                } else {
                    log.warn("【账号{}】自动发货图片[{}/{}]发送失败: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                }
            } catch (Exception e) {
                log.error("【账号{}】自动发货图片[{}/{}]发送异常: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId, e);
            }
        }
    }

    private void executeAutoConfirmShipment(Long accountId, String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("【账号{}】订单ID为空，无法自动确认发货", accountId);
            return;
        }
        log.info("【账号{}】提交异步自动确认发货: orderId={}", accountId, orderId);
        new Thread(() -> {
            try {
                XianyuGoodsOrder existingOrder = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
                if (existingOrder != null && existingOrder.getConfirmState() != null && existingOrder.getConfirmState() == 1) {
                    log.info("【账号{}】订单已确认发货，跳过自动确认发货: orderId={}", accountId, orderId);
                    return;
                }
                HumanLikeDelayUtils.longDelay();
                String result = orderService.confirmShipment(accountId, orderId);
                if (result != null) {
                    log.info("【账号{}】✅ 自动确认发货成功: orderId={}", accountId, orderId);
                    orderMapper.updateConfirmState(accountId, orderId);
                    XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
                    deliveryMessageSettingService.sendOrderStatusMessage(DeliveryMessageSettingService.EVENT_SHIPPED, order);
                } else {
                    log.error("【账号{}】❌ 自动确认发货失败: orderId={}", accountId, orderId);
                }
            } catch (Exception e) {
                log.error("【账号{}】自动确认发货异常: orderId={}", accountId, orderId, e);
            }
        }).start();
    }

    private void updateRecordState(Long recordId, Integer state, String content, String failReason) {
        try {
            orderMapper.updateStateContentAndFailReason(recordId, state, content, failReason);
        } catch (Exception e) {
            log.error("更新订单状态失败: recordId={}, state={}", recordId, state, e);
        }
    }

    @Override
    public void returnApiDeliveryForOrder(Long accountId, String orderId, String reason) {
        if (accountId == null || isBlank(orderId)) {
            return;
        }
        XianyuGoodsOrder record = orderMapper.selectReturnableApiDeliveryByOrder(accountId, orderId);
        if (record == null) {
            return;
        }
        XianyuGoodsAutoDeliveryConfig config = selectDeliveryConfig(accountId, record.getXyGoodsId(), record);
        if (config == null || !isApiMode(config)) {
            log.warn("【账号{}】订单存在API占用记录，但当前规则不可用: orderId={}", accountId, orderId);
            return;
        }
        returnApiDelivery(config, record, record.getExternalAllocationId(), reason);
    }

    @Override
    public void updateAutoConfirmShipment(Long accountId, String xyGoodsId, Integer autoConfirmShipment) {
        XianyuGoodsAutoDeliveryConfig config = autoDeliveryConfigMapper.findByAccountIdAndGoodsId(accountId, xyGoodsId);
        if (config == null) {
            config = new XianyuGoodsAutoDeliveryConfig();
            config.setXianyuAccountId(accountId);
            config.setXyGoodsId(xyGoodsId);
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.updateById(config);
        }
    }

    private DeliveryContent acquireKamiContent(String kamiConfigIds, String kamiDeliveryTemplate, String orderId, Long accountId, String xyGoodsId, String sId, String buyerUserName) {
        return acquireKamiContent(kamiConfigIds, kamiDeliveryTemplate, orderId, accountId, xyGoodsId, sId, buyerUserName, 1, false);
    }

    private DeliveryContent acquireKamiContent(String kamiConfigIds, String kamiDeliveryTemplate, String orderId, Long accountId, String xyGoodsId, String sId, String buyerUserName, int quantity, boolean separateSendMode) {
        if (kamiConfigIds == null || kamiConfigIds.trim().isEmpty()) {
            log.warn("【账号{}】卡密发货未绑定卡密配置: xyGoodsId={}", accountId, xyGoodsId);
            return null;
        }

        String[] configIdArr = kamiConfigIds.split(",");
        int requestedQuantity = Math.max(1, quantity);
        List<AcquiredKami> acquiredKamis = new ArrayList<>();

        for (int i = 0; i < requestedQuantity; i++) {
            XianyuKamiItem kamiItem = null;
            Long usedConfigId = null;
            for (String configIdStr : configIdArr) {
                try {
                    Long configId = Long.parseLong(configIdStr.trim());
                    kamiItem = kamiConfigService.acquireKami(configId, orderId);
                    if (kamiItem != null) {
                        usedConfigId = configId;
                        break;
                    }
                } catch (NumberFormatException e) {
                    log.warn("【账号{}】卡密配置ID格式错误: {}", accountId, configIdStr);
                }
            }
            if (kamiItem == null) {
                log.warn("【账号{}】卡密不足，已获取{}/{}个: xyGoodsId={}", accountId, acquiredKamis.size(), requestedQuantity, xyGoodsId);
                releaseAcquiredKamis(acquiredKamis);
                return null;
            }
            acquiredKamis.add(new AcquiredKami(usedConfigId, kamiItem));
            log.info("【账号{}】卡密扣减成功({}/{}): configId={}, itemId={}, orderId={}", accountId, i + 1, requestedQuantity, usedConfigId, kamiItem.getId(), orderId);
        }

        if (acquiredKamis.isEmpty()) {
            return null;
        }

        List<String> rawKamiContents = new ArrayList<>();
        for (AcquiredKami acquiredKami : acquiredKamis) {
            XianyuKamiItem kamiItem = acquiredKami.item();
            XianyuKamiUsageRecord usageRecord = new XianyuKamiUsageRecord();
            usageRecord.setKamiConfigId(acquiredKami.configId());
            usageRecord.setKamiItemId(kamiItem.getId());
            usageRecord.setXianyuAccountId(accountId);
            usageRecord.setXyGoodsId(xyGoodsId);
            usageRecord.setOrderId(orderId);
            usageRecord.setKamiContent(kamiItem.getKamiContent());
            String cid = sId.replace("@goofish", "");
            usageRecord.setBuyerUserId(cid);
            usageRecord.setBuyerUserName(buyerUserName);
            kamiUsageRecordMapper.insert(usageRecord);
            rawKamiContents.add(kamiItem.getKamiContent());
        }

        if (separateSendMode) {
            List<String> messageContents = rawKamiContents.stream()
                    .map(k -> applyKamiTemplate(k, kamiDeliveryTemplate))
                    .toList();
            return new DeliveryContent(messageContents, String.join("\n", rawKamiContents), requestedQuantity);
        }

        // 合并为一条消息
        String mergedRaw = String.join("\n", rawKamiContents);
        String kamiContent = applyKamiTemplate(mergedRaw, kamiDeliveryTemplate);
        return new DeliveryContent(kamiContent, mergedRaw, requestedQuantity);
    }

    private DeliveryContent acquireApiDeliveryContent(XianyuGoodsAutoDeliveryConfig deliveryConfig, Long recordId,
                                                      Long accountId, String xyGoodsId, String sId, String orderId,
                                                      String buyerUserName, int quantity, boolean separateSendMode) {
        int requestedQuantity = resolveRequestedQuantity(quantity);
        List<ApiAllocation> allocations = new ArrayList<>();
        for (int i = 1; i <= requestedQuantity; i++) {
            ApiDeliveryContext context = buildApiDeliveryContext(deliveryConfig, recordId, accountId, xyGoodsId, sId,
                    orderId, buyerUserName, null, requestedQuantity, i);
            ApiDeliveryResult result = apiDeliveryService.allocate(deliveryConfig, context);
            if (!result.isSuccess()) {
                String reason = "API发货分配失败: " + safeText(result.getMessage());
                log.warn("【账号{}】{}: xyGoodsId={}, orderId={}, index={}/{}",
                        accountId, reason, xyGoodsId, orderId, i, requestedQuantity);
                returnApiAllocations(deliveryConfig, recordId, allocations, "多件API发货分配失败回库");
                updateRecordState(recordId, -1, null, reason);
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, reason);
                return null;
            }
            String allocationId = result.getAllocationId();
            if (isBlank(allocationId)) {
                allocationId = orderId + "-" + i;
            }
            String finalContent = ApiDeliveryServiceImpl.renderApiDeliveryMessage(
                    deliveryConfig.getApiDeliveryTemplate(),
                    result.getContent()
            );
            allocations.add(new ApiAllocation(allocationId, finalContent));
        }
        String allocationIds = allocations.stream()
                .map(ApiAllocation::allocationId)
                .collect(Collectors.joining(","));
        orderMapper.updateExternalAllocation(recordId, allocationIds);
        log.info("【账号{}】API发货分配成功: recordId={}, allocationIds={}", accountId, recordId, allocationIds);

        List<String> messageContents = allocations.stream().map(ApiAllocation::content).toList();
        if (separateSendMode) {
            return new DeliveryContent(messageContents, null, allocationIds, true, requestedQuantity);
        }
        return new DeliveryContent(List.of(String.join("\n", messageContents)), null, allocationIds, true, requestedQuantity);
    }

    private void returnApiAllocations(XianyuGoodsAutoDeliveryConfig config, Long recordId,
                                      List<ApiAllocation> allocations, String reason) {
        for (ApiAllocation allocation : allocations) {
            XianyuGoodsOrder record = orderMapper.selectById(recordId);
            if (record == null) {
                continue;
            }
            returnApiDelivery(config, record, allocation.allocationId(), reason);
        }
    }

    private void releaseAcquiredKamis(List<AcquiredKami> acquiredKamis) {
        for (AcquiredKami acquiredKami : acquiredKamis) {
            try {
                kamiConfigService.resetKamiItem(acquiredKami.item().getId());
            } catch (Exception e) {
                log.warn("回滚卡密占用失败: itemId={}", acquiredKami.item().getId(), e);
            }
        }
    }

    private String applyKamiTemplate(String kamiContent, String template) {
        if (template != null && !template.trim().isEmpty()) {
            return template.replace("{kmKey}", kamiContent);
        }
        return kamiContent;
    }

    private static class DeliveryContent {
        private final List<String> messageContents;
        private final String rawKamiContent;
        private final String allocationId;
        private final boolean apiDelivery;
        private final int deliveredQuantity;
        private boolean mainDeliveryComplete;

        private DeliveryContent(String messageContent, String rawKamiContent) {
            this(messageContent, rawKamiContent, 1);
        }

        private DeliveryContent(String messageContent, String rawKamiContent, int deliveredQuantity) {
            this(List.of(messageContent), rawKamiContent, null, false, deliveredQuantity);
        }

        private DeliveryContent(String messageContent, String rawKamiContent, String allocationId, boolean apiDelivery) {
            this(List.of(messageContent), rawKamiContent, allocationId, apiDelivery, 1);
        }

        private DeliveryContent(List<String> messageContents, String rawKamiContent, int deliveredQuantity) {
            this(messageContents, rawKamiContent, null, false, deliveredQuantity);
        }

        private DeliveryContent(List<String> messageContents, String rawKamiContent, String allocationId,
                                boolean apiDelivery, int deliveredQuantity) {
            this.messageContents = messageContents == null || messageContents.isEmpty() ? List.of("") : List.copyOf(messageContents);
            this.rawKamiContent = rawKamiContent;
            this.allocationId = allocationId;
            this.apiDelivery = apiDelivery;
            this.deliveredQuantity = Math.max(1, deliveredQuantity);
        }

        private String getMessageContent() {
            return String.join("\n", messageContents);
        }

        private List<String> getMessageContents() {
            return messageContents;
        }

        private String getRawKamiContent() {
            return rawKamiContent;
        }

        private String getAllocationId() {
            return allocationId;
        }

        private boolean isApiDelivery() {
            return apiDelivery;
        }

        private int getDeliveredQuantity() {
            return deliveredQuantity;
        }

        private void markMainDeliveryComplete() {
            this.mainDeliveryComplete = true;
        }

        private boolean isMainDeliveryComplete() {
            return mainDeliveryComplete;
        }
    }

    private record AcquiredKami(Long configId, XianyuKamiItem item) {
    }

    private boolean sendDeliveryMessagesWithRetry(Long accountId, String cid, String toId, DeliveryContent deliveryContent) {
        for (String messageContent : deliveryContent.getMessageContents()) {
            if (!sendDeliveryTextWithRetry(accountId, cid, toId, messageContent)) {
                return false;
            }
        }
        return true;
    }

    private boolean sendPostDeliveryTextIfNeeded(Long accountId, String cid, String toId, String xyGoodsId,
                                                 XianyuGoodsAutoDeliveryConfig deliveryConfig) {
        String postText = deliveryConfig.getPostDeliveryText();
        if (isBlank(postText)) {
            return true;
        }
        String content = postText.trim();
        boolean success = sendDeliveryTextWithRetry(accountId, cid, toId, content);
        if (success) {
            sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
            log.info("【账号{}】发货后文本发送成功: xyGoodsId={}", accountId, xyGoodsId);
        } else {
            log.warn("【账号{}】发货后文本发送失败: xyGoodsId={}", accountId, xyGoodsId);
        }
        return success;
    }

    private void saveMainDeliveryReplies(Long accountId, String cid, String toId, String xyGoodsId,
                                         DeliveryContent deliveryContent) {
        for (String messageContent : deliveryContent.getMessageContents()) {
            sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, messageContent, xyGoodsId);
        }
    }

    private boolean isMainDeliveryComplete(DeliveryContent deliveryContent) {
        return deliveryContent != null && deliveryContent.isMainDeliveryComplete();
    }

    private boolean sendDeliveryTextWithRetry(Long accountId, String cid, String toId, String content) {
        int attempts = Math.max(1, webSocketConfig.getMessageRetryAttempts());
        long delay = Math.max(0L, webSocketConfig.getMessageRetryDelay());
        for (int i = 1; i <= attempts; i++) {
            boolean success = webSocketService.sendMessageWithResult(accountId, cid, toId, content);
            if (success) {
                if (i > 1) {
                    log.info("【账号{}】发货文本重试成功: attempt={}/{}", accountId, i, attempts);
                }
                return true;
            }
            log.warn("【账号{}】发货文本发送失败，准备重试: attempt={}/{}", accountId, i, attempts);
            if (i < attempts && delay > 0) {
                sleepRetryDelay(delay);
            }
        }
        return false;
    }

    private void sleepRetryDelay(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildManualPnmId(String orderId, String sId) {
        if (!isBlank(orderId)) {
            return "manual-order-" + orderId.trim();
        }
        if (!isBlank(sId)) {
            return "manual-sid-" + sId.trim() + "-" + System.currentTimeMillis();
        }
        return "manual-" + System.currentTimeMillis();
    }

    private boolean abortDeliveryForClosedOrderIfNeeded(Long accountId, String orderId, int deliveryMode,
                                                        XianyuGoodsAutoDeliveryConfig config, Long recordId,
                                                        DeliveryContent content) {
        String reason = resolveClosedOrderReason(accountId, orderId);
        if (reason == null) {
            return false;
        }
        updateRecordState(recordId, -1, content != null ? content.getMessageContent() : null, reason);
        returnApiDeliveryIfNeeded(deliveryMode, config, recordId, content, reason);
        return true;
    }

    private String resolveClosedOrderReason(Long accountId, String orderId) {
        if (accountId == null || isBlank(orderId)) {
            return null;
        }
        XianyuOrder order = xianyuOrderMapper.selectOne(
                new LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getXianyuAccountId, accountId)
                        .eq(XianyuOrder::getOrderId, orderId)
                        .last("LIMIT 1")
        );
        if (order == null || !isReturnableStatus(order.getOrderStatus(), order.getOrderStatusText())) {
            return null;
        }
        return "订单状态已变更为" + safeOrderStatusText(order.getOrderStatusText());
    }

    private boolean isReturnableStatus(Integer status, String statusText) {
        if (status != null && status == 5) {
            return true;
        }
        if (isBlank(statusText)) {
            return false;
        }
        return statusText.contains("关闭")
                || statusText.contains("取消")
                || statusText.contains("退款")
                || statusText.contains("退货");
    }

    private String safeOrderStatusText(String statusText) {
        return isBlank(statusText) ? "取消/关闭/退款" : statusText.trim();
    }

    private void confirmApiDeliveryIfNeeded(int deliveryMode, XianyuGoodsAutoDeliveryConfig config,
                                            XianyuGoodsOrder record, DeliveryContent content) {
        if (deliveryMode != DELIVERY_MODE_API || content == null || !content.isApiDelivery()) {
            return;
        }
        boolean allSuccess = true;
        String lastMessage = null;
        for (String allocationId : splitAllocationIds(content.getAllocationId())) {
            ApiDeliveryContext context = buildApiDeliveryContext(config, record, allocationId, null);
            ApiDeliveryResult result = apiDeliveryService.confirm(config, context);
            allSuccess = allSuccess && result.isSuccess();
            lastMessage = result.getMessage();
            if (!result.isSuccess()) {
                log.warn("【账号{}】API发货确认失败: recordId={}, allocationId={}, reason={}",
                        record.getXianyuAccountId(), record.getId(), allocationId, result.getMessage());
            }
        }
        orderMapper.updateExternalConfirmState(record.getId(), allSuccess ? 1 : -1);
        if (!allSuccess && lastMessage != null) {
            log.warn("【账号{}】API发货存在确认失败: recordId={}, reason={}", record.getXianyuAccountId(), record.getId(), lastMessage);
        }
    }

    private void returnApiDeliveryIfNeeded(int deliveryMode, XianyuGoodsAutoDeliveryConfig config,
                                           Long recordId, DeliveryContent content, String reason) {
        if (deliveryMode != DELIVERY_MODE_API) {
            return;
        }
        XianyuGoodsOrder record = orderMapper.selectById(recordId);
        if (record == null || isBlank(record.getExternalAllocationId())) {
            return;
        }
        for (String allocationId : splitAllocationIds(record.getExternalAllocationId())) {
            returnApiDelivery(config, record, allocationId, reason);
        }
    }

    private void returnApiDelivery(XianyuGoodsAutoDeliveryConfig config, XianyuGoodsOrder record,
                                   String allocationId, String reason) {
        ApiDeliveryContext context = buildApiDeliveryContext(config, record, allocationId, reason);
        ApiDeliveryResult result = apiDeliveryService.returnAllocation(config, context);
        orderMapper.updateExternalReturnState(record.getId(), result.isSuccess() ? 1 : -1,
                result.isSuccess() ? reason : result.getMessage());
        if (!result.isSuccess()) {
            log.warn("【账号{}】API发货回库失败: recordId={}, allocationId={}, reason={}",
                    record.getXianyuAccountId(), record.getId(), allocationId, result.getMessage());
        }
    }

    private ApiDeliveryContext buildApiDeliveryContext(XianyuGoodsAutoDeliveryConfig config, XianyuGoodsOrder record,
                                                       String allocationId, String reason) {
        return ApiDeliveryContext.builder()
                .recordId(record.getId())
                .accountId(record.getXianyuAccountId())
                .xianyuGoodsId(record.getXianyuGoodsId())
                .xyGoodsId(record.getXyGoodsId())
                .sId(record.getSid())
                .orderId(record.getOrderId())
                .buyerUserId(record.getBuyerUserId())
                .buyerUserName(record.getBuyerUserName())
                .triggerSource(record.getTriggerSource())
                .triggerContent(record.getTriggerContent())
                .buyQuantity(1)
                .deliveryIndex(1)
                .deliveryTotal(1)
                .ruleId(config.getId())
                .ruleName(resolveRuleName(config))
                .allocationId(allocationId)
                .reason(reason)
                .apiRequestExtras(config.getApiRequestExtras())
                .build();
    }

    private ApiDeliveryContext buildApiDeliveryContext(XianyuGoodsAutoDeliveryConfig config, Long recordId,
                                                       Long accountId, String xyGoodsId, String sId,
                                                       String orderId, String buyerUserName, String reason) {
        return buildApiDeliveryContext(config, recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, reason, 1, 1);
    }

    private ApiDeliveryContext buildApiDeliveryContext(XianyuGoodsAutoDeliveryConfig config, Long recordId,
                                                       Long accountId, String xyGoodsId, String sId,
                                                       String orderId, String buyerUserName, String reason,
                                                       int deliveryTotal, int deliveryIndex) {
        XianyuGoodsOrder record = orderMapper.selectById(recordId);
        if (record != null) {
            ApiDeliveryContext context = buildApiDeliveryContext(config, record, record.getExternalAllocationId(), reason);
            context.setBuyQuantity(deliveryTotal);
            context.setDeliveryTotal(deliveryTotal);
            context.setDeliveryIndex(deliveryIndex);
            return context;
        }
        return ApiDeliveryContext.builder()
                .recordId(recordId)
                .accountId(accountId)
                .xyGoodsId(xyGoodsId)
                .sId(sId)
                .orderId(orderId)
                .buyerUserName(buyerUserName)
                .buyQuantity(deliveryTotal)
                .deliveryIndex(deliveryIndex)
                .deliveryTotal(deliveryTotal)
                .ruleId(config.getId())
                .ruleName(resolveRuleName(config))
                .reason(reason)
                .apiRequestExtras(config.getApiRequestExtras())
                .build();
    }

    private List<String> splitAllocationIds(String allocationIds) {
        if (isBlank(allocationIds)) {
            return List.of();
        }
        return List.of(allocationIds.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private record ApiAllocation(String allocationId, String content) {
    }
}
