package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuChatMessageMapper;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.service.OperationLogService;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 会保存所有聊天消息
 * 
 * <p>监听 {@link ChatMessageReceivedEvent} 事件，负责将消息异步保存到数据库</p>
 * 
 * <p>特点：</p>
 * <ul>
 *   <li>使用@Async异步执行，不阻塞WebSocket消息接收</li>
 *   <li>自动去重，避免重复保存</li>
 *   <li>独立模块，与其他监听器互不影响</li>
 * </ul>
 * 
 * @author feijimiao
 * @since 1.0
 */
@Slf4j
@Component
public class ChatMessageEventSaveListener {
    
    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;

    @Autowired
    private GoodsInfoService goodsInfoService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ChatMessageEventSseListener sseListener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理聊天消息接收事件 - 保存消息到数据库
     * 
     * @param event 聊天消息接收事件
     */
    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessageData messageData = event.getMessageData();
        
        // 转换为数据库实体
        XianyuChatMessage message = new XianyuChatMessage();
        org.springframework.beans.BeanUtils.copyProperties(messageData, message);
        
        log.info("【账号{}】[SaveListener]收到ChatMessageReceivedEvent事件: pnmId={}, contentType={}, msgContent={}, orderId={}", 
                message.getXianyuAccountId(), message.getPnmId(), message.getContentType(), message.getMsgContent(), messageData.getOrderId());
        
        try {
            // 检查消息是否已存在（去重）
            XianyuChatMessage existing = chatMessageMapper.findByPnmId(
                    message.getXianyuAccountId(), message.getPnmId());
            
            if (existing != null) {
                log.info("【账号{}】[SaveListener]消息已存在，跳过保存: pnmId={}", 
                        message.getXianyuAccountId(), message.getPnmId());
                ensureMessageGoods(messageData);
                sseListener.broadcastSavedMessage(messageData, existing.getId());
                return;
            }
            
            // 保存消息到数据库
            int result = chatMessageMapper.insert(message);
            
            if (result > 0) {
                log.info("【账号{}】[SaveListener]消息保存成功: pnmId={}, id={}",
                        message.getXianyuAccountId(), message.getPnmId(), message.getId());
                logMessageSaved(messageData, message.getId());
                ensureMessageGoods(messageData);
                sseListener.broadcastSavedMessage(messageData, message.getId());
            } else {
                log.error("【账号{}】[SaveListener]保存消息失败: pnmId={}",
                        message.getXianyuAccountId(), message.getPnmId());
                logMessageSaveFailed(messageData, "数据库插入返回0");
            }
            
        } catch (Exception e) {
            // 检查是否是唯一约束冲突（消息已存在）
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                log.info("【账号{}】[SaveListener]消息ID冲突，跳过保存: pnmId={}", 
                        message.getXianyuAccountId(), message.getPnmId());
                XianyuChatMessage existing = chatMessageMapper.findByPnmId(
                        message.getXianyuAccountId(), message.getPnmId());
                if (existing != null) {
                    ensureMessageGoods(messageData);
                    sseListener.broadcastSavedMessage(messageData, existing.getId());
                }
            } else {
                log.error("【账号{}】[SaveListener]异步保存消息异常: pnmId={}, error={}",
                        message.getXianyuAccountId(), message.getPnmId(), e.getMessage(), e);
                logMessageSaveFailed(messageData, e.getMessage());
            }
        }
    }

    private void logMessageSaved(ChatMessageData messageData, Long messageId) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("messageId", messageId);
            params.put("pnmId", safeText(messageData.getPnmId()));
            params.put("sId", safeText(messageData.getSId()));
            params.put("contentType", messageData.getContentType());
            params.put("senderUserId", safeText(messageData.getSenderUserId()));
            params.put("senderUserName", safeText(messageData.getSenderUserName()));
            params.put("xyGoodsId", safeText(messageData.getXyGoodsId()));
            params.put("orderId", safeText(messageData.getOrderId()));
            params.put("messageLength", messageData.getMsgContent() != null ? messageData.getMsgContent().length() : 0);
            operationLogService.log(
                    messageData.getXianyuAccountId(),
                    OperationConstants.Type.RECEIVE,
                    OperationConstants.Module.MESSAGE,
                    "收到新消息并入库",
                    OperationConstants.Status.SUCCESS,
                    OperationConstants.TargetType.MESSAGE,
                    messageData.getPnmId(),
                    objectMapper.writeValueAsString(params),
                    null,
                    null,
                    null);
        } catch (Exception e) {
            log.warn("【账号{}】记录新消息操作日志失败: pnmId={}", messageData.getXianyuAccountId(), messageData.getPnmId(), e);
        }
    }

    private void logMessageSaveFailed(ChatMessageData messageData, String reason) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("pnmId", safeText(messageData.getPnmId()));
            params.put("sId", safeText(messageData.getSId()));
            params.put("contentType", messageData.getContentType());
            params.put("xyGoodsId", safeText(messageData.getXyGoodsId()));
            params.put("orderId", safeText(messageData.getOrderId()));
            operationLogService.log(
                    messageData.getXianyuAccountId(),
                    OperationConstants.Type.RECEIVE,
                    OperationConstants.Module.MESSAGE,
                    "收到新消息但入库失败",
                    OperationConstants.Status.FAIL,
                    OperationConstants.TargetType.MESSAGE,
                    messageData.getPnmId(),
                    objectMapper.writeValueAsString(params),
                    null,
                    reason,
                    null);
        } catch (Exception e) {
            log.warn("【账号{}】记录消息入库失败操作日志异常: pnmId={}", messageData.getXianyuAccountId(), messageData.getPnmId(), e);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void ensureMessageGoods(ChatMessageData messageData) {
        try {
            if (messageData.getXyGoodsId() == null || messageData.getXyGoodsId().trim().isEmpty()) {
                return;
            }
            goodsInfoService.ensurePlaceholderGoods(
                    messageData.getXianyuAccountId(),
                    messageData.getXyGoodsId(),
                    messageData.getReminderUrl(),
                    null);
        } catch (Exception e) {
            log.warn("【账号{}】[SaveListener]沉淀消息商品失败: pnmId={}, xyGoodsId={}, error={}",
                    messageData.getXianyuAccountId(), messageData.getPnmId(), messageData.getXyGoodsId(), e.getMessage());
        }
    }
}

