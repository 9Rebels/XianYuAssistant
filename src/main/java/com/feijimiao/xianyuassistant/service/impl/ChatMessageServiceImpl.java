package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.entity.XianyuConversationState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuChatMessageMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.controller.dto.MsgContextReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgListReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgListRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.OnlineConversationDTO;
import com.feijimiao.xianyuassistant.service.ChatMessageService;
import com.feijimiao.xianyuassistant.service.ConversationReadStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 聊天消息服务实现
 * 
 * <p>职责：提供消息查询相关的服务</p>
 * <p>注意：WebSocket 消息的解析和保存现在由 SyncMessageHandler 直接处理</p>
 */
@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final Set<Integer> CARD_CONTENT_TYPES = Set.of(25, 26, 28, 32);
    private static final Set<String> STANDALONE_NOTICE_NAMES = Set.of("工作台通知");
    private static final Set<String> SYNTHETIC_PEER_NAMES = Set.of(
            "交易消息",
            "工作台通知",
            "系统通知",
            "通知消息",
            "买家已拍下，待付款",
            "我已拍下，待付款",
            "我已付款，等待你发货",
            "买家确认收货，交易成功",
            "快给ta一个评价吧～",
            "记得及时发货"
    );
    
    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;
    
    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private XianyuOrderMapper orderMapper;

    @Autowired
    private XianyuGoodsOrderMapper goodsOrderMapper;

    @Autowired
    private ConversationReadStateService readStateService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final XianyuRichMessageParser richMessageParser = new XianyuRichMessageParser(objectMapper);

    @Override
    public List<XianyuChatMessage> getMessagesByAccountId(Long accountId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return chatMessageMapper.findByAccountId(accountId, pageSize, offset);
    }
    
    @Override
    public List<XianyuChatMessage> getMessagesBySessionId(String sessionId) {
        return chatMessageMapper.findBySId(sessionId);
    }
    
    @Override
    public ResultObject<MsgListRespDTO> getMessageList(MsgListReqDTO reqDTO) {
        try {
            // 参数验证
            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.validateFailed("xianyuAccountId不能为空");
            }
            
            // 设置默认值
            int pageNum = reqDTO.getPageNum() != null && reqDTO.getPageNum() > 0 ? reqDTO.getPageNum() : 1;
            int pageSize = reqDTO.getPageSize() != null && reqDTO.getPageSize() > 0 ? reqDTO.getPageSize() : 20;
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 获取当前账号的UNB（用于过滤）
            String currentAccountUnb = null;
            if (reqDTO.getFilterCurrentAccount() != null && reqDTO.getFilterCurrentAccount()) {
                XianyuAccount account = accountMapper.selectById(reqDTO.getXianyuAccountId());
                if (account != null) {
                    currentAccountUnb = account.getUnb();
                }
            }
            
            // 查询总数
            int totalCount = chatMessageMapper.countMessages(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    currentAccountUnb
            );
            
            // 查询分页数据
            List<XianyuChatMessage> messages = chatMessageMapper.findMessagesByPage(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    currentAccountUnb,
                    pageSize,
                    offset
            );
            
            // 转换为DTO
            List<MsgDTO> msgDTOList = new ArrayList<>();
            if (messages != null) {
                for (XianyuChatMessage message : messages) {
                    msgDTOList.add(toMsgDTO(message));
                }
            }
            
            // 计算总页数
            int totalPage = (int) Math.ceil((double) totalCount / pageSize);
            if (totalPage == 0 && totalCount > 0) {
                totalPage = 1;
            }
            
            // 构建响应
            MsgListRespDTO respDTO = new MsgListRespDTO();
            respDTO.setList(msgDTOList);
            respDTO.setTotalCount(totalCount);
            respDTO.setPageNum(pageNum);
            respDTO.setPageSize(pageSize);
            respDTO.setTotalPage(totalPage);
            
            return ResultObject.success(respDTO);
            
        } catch (Exception e) {
            log.error("查询消息列表失败: accountId={}, xyGoodsId={}, filterCurrentAccount={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getFilterCurrentAccount(), e);
            return ResultObject.failed("查询消息列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<?> getContextMessages(MsgContextReqDTO reqDTO) {
        try {
            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.validateFailed("xianyuAccountId不能为空");
            }
            if (reqDTO.getSid() == null || reqDTO.getSid().isEmpty()) {
                return ResultObject.validateFailed("sid不能为空");
            }
            
            int limit = reqDTO.getLimit() != null && reqDTO.getLimit() > 0 ? reqDTO.getLimit() : 20;
            int offset = reqDTO.getOffset() != null && reqDTO.getOffset() >= 0 ? reqDTO.getOffset() : 0;
            XianyuAccount account = accountMapper != null ? accountMapper.selectById(reqDTO.getXianyuAccountId()) : null;
            String currentAccountUnb = account != null ? account.getUnb() : null;
            
            List<XianyuChatMessage> messages = chatMessageMapper.findRecentByAccountIdAndSId(
                    reqDTO.getXianyuAccountId(), reqDTO.getSid(), limit, offset);
            
            List<MsgDTO> msgDTOList = new ArrayList<>();
            if (messages != null) {
                for (XianyuChatMessage message : messages) {
                    if (!isMessageForAccount(message, reqDTO.getXianyuAccountId(), currentAccountUnb)) {
                        continue;
                    }
                    msgDTOList.add(toMsgDTO(message));
                }
            }
            
            return ResultObject.success(msgDTOList);
            
        } catch (Exception e) {
            log.error("查询上下文消息失败: accountId={}, sid={}", reqDTO.getXianyuAccountId(), reqDTO.getSid(), e);
            return ResultObject.failed("查询上下文消息失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<OnlineConversationDTO>> getOnlineConversations(Long accountId, Integer limit) {
        if (accountId == null) {
            return ResultObject.validateFailed("xianyuAccountId不能为空");
        }

        int conversationLimit = normalizeConversationLimit(limit);
        int scanLimit = Math.min(600, Math.max(conversationLimit * 12, 160));
        XianyuAccount account = accountMapper.selectById(accountId);
        String currentAccountUnb = account != null ? account.getUnb() : null;
        ConversationEnrichCache cache = new ConversationEnrichCache();

        List<XianyuChatMessage> messages = chatMessageMapper.findRecentForConversations(accountId, scanLimit);
        Map<String, OnlineConversationDTO> conversations = new LinkedHashMap<>();
        Map<String, XianyuChatMessage> latestMessageBySid = new HashMap<>();

        for (XianyuChatMessage message : messages) {
            if (!isMessageForAccount(message, accountId, currentAccountUnb)) {
                continue;
            }
            if (isStandaloneNoticeMessage(message)) {
                rememberNoticeAvatar(cache, message);
                continue;
            }
            String sid = message.getSId();
            if (sid == null || sid.isBlank()) {
                continue;
            }

            OnlineConversationDTO conversation = conversations.computeIfAbsent(sid, key -> {
                latestMessageBySid.put(sid, message);
                return buildConversation(message);
            });
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            if (conversation.getMessageCount() > 1 && isConversationEnrichedEnough(conversation)) {
                if (conversations.size() >= conversationLimit && allConversationsHavePeer(conversations)) {
                    break;
                }
                continue;
            }

            enrichPeer(conversation, message, currentAccountUnb, cache);
            if (needsGoodsEnrich(conversation, message)) {
                enrichGoods(conversation, message, cache);
            }
            if (needsOrderEnrich(conversation)) {
                enrichOrder(conversation, message, cache);
            }
            XianyuChatMessage latestMessage = latestMessageBySid.get(sid);
            if (needsCardEnrich(conversation, message) && isLatestMessageCard(latestMessage)) {
                enrichCard(conversation, message, cache);
            }

            if (conversations.size() >= conversationLimit && allConversationsHavePeer(conversations)) {
                break;
            }
        }

        List<OnlineConversationDTO> result = new ArrayList<>(conversations.values());
        result.forEach(conversation -> finalizeConversation(conversation, cache));
        enrichReadStates(accountId, result);
        return ResultObject.success(result);
    }

    private boolean isLatestMessageCard(XianyuChatMessage message) {
        if (message == null) {
            return false;
        }
        Integer contentType = message.getContentType();
        if (contentType != null && CARD_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        String text = message.getMsgContent();
        return text != null && (text.contains("卡片消息") || text.contains("[卡片消息]") || text.contains("商品卡片") || text.contains("订单卡片"));
    }

    private int normalizeConversationLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }

    private OnlineConversationDTO buildConversation(XianyuChatMessage message) {
        OnlineConversationDTO conversation = new OnlineConversationDTO();
        conversation.setSid(message.getSId());
        conversation.setXianyuAccountId(message.getXianyuAccountId());
        conversation.setLastMessage(message.getMsgContent());
        conversation.setLastContentType(message.getContentType());
        conversation.setLastMessageTime(message.getMessageTime());
        conversation.setXyGoodsId(message.getXyGoodsId());
        conversation.setUnreadCount(0);
        conversation.setReadStatus(null);
        conversation.setReadStatusText("");
        conversation.setMessageCount(0);
        return conversation;
    }

    private void enrichReadStates(Long accountId, List<OnlineConversationDTO> conversations) {
        List<String> sIds = conversations.stream()
                .map(OnlineConversationDTO::getSid)
                .filter(this::hasText)
                .distinct()
                .toList();
        Map<String, XianyuConversationState> states = readStateService.findStates(accountId, sIds);
        conversations.forEach(conversation -> {
            XianyuConversationState state = states.get(conversation.getSid());
            if (state == null || state.getReadStatus() == null) {
                return;
            }
            conversation.setReadStatus(state.getReadStatus());
            conversation.setReadTimestamp(state.getReadTimestamp());
            conversation.setReadStatusText(state.getReadStatus() == 1 ? "已读" : "未读");
        });
    }

    private boolean needsGoodsEnrich(OnlineConversationDTO conversation, XianyuChatMessage message) {
        return (conversation.getXyGoodsId() == null || conversation.getXyGoodsId().isBlank())
                && message.getXyGoodsId() != null && !message.getXyGoodsId().isBlank()
                || conversation.getGoodsTitle() == null || conversation.getGoodsTitle().isBlank()
                || conversation.getGoodsCoverPic() == null || conversation.getGoodsCoverPic().isBlank()
                || conversation.getGoodsPrice() == null || conversation.getGoodsPrice().isBlank()
                || conversation.getGoodsStatus() == null;
    }

    private boolean needsOrderEnrich(OnlineConversationDTO conversation) {
        return conversation.getOrderId() == null || conversation.getOrderId().isBlank()
                || conversation.getOrderStatus() == null
                || conversation.getOrderStatusText() == null || conversation.getOrderStatusText().isBlank()
                || conversation.getOrderAmountText() == null || conversation.getOrderAmountText().isBlank()
                || conversation.getAutoDeliveryState() == null
                || conversation.getAutoDeliveryStateText() == null || conversation.getAutoDeliveryStateText().isBlank();
    }

    private boolean needsCardEnrich(OnlineConversationDTO conversation, XianyuChatMessage message) {
        if (!mightContainCard(conversation, message)) {
            return false;
        }
        return conversation.getCardTitle() == null || conversation.getCardTitle().isBlank()
                || conversation.getCardSubtitle() == null || conversation.getCardSubtitle().isBlank()
                || conversation.getCardImageUrl() == null || conversation.getCardImageUrl().isBlank()
                || conversation.getCardActionText() == null || conversation.getCardActionText().isBlank()
                || conversation.getCardTag() == null || conversation.getCardTag().isBlank();
    }

    private boolean mightContainCard(OnlineConversationDTO conversation, XianyuChatMessage message) {
        if (hasText(conversation.getCardTitle()) || hasText(conversation.getCardSubtitle())) {
            return true;
        }
        Integer contentType = message.getContentType();
        if (contentType != null && CARD_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        String text = message.getMsgContent();
        return text != null && (text.contains("卡片消息") || text.contains("[卡片消息]") || text.contains("商品卡片") || text.contains("订单卡片"));
    }

    private boolean isConversationEnrichedEnough(OnlineConversationDTO conversation) {
        boolean hasPeer = hasText(conversation.getPeerUserId()) || hasText(conversation.getPeerUserName());
        boolean hasGoods = !hasText(conversation.getXyGoodsId()) || hasText(conversation.getGoodsTitle());
        boolean hasOrder = !hasText(conversation.getXyGoodsId())
                || conversation.getOrderStatus() != null
                || conversation.getAutoDeliveryState() != null
                || hasText(conversation.getOrderId());
        return hasPeer && hasGoods && hasOrder;
    }

    private MsgDTO toMsgDTO(XianyuChatMessage message) {
        MsgDTO msgDTO = new MsgDTO();
        msgDTO.setId(message.getId());
        msgDTO.setSId(message.getSId());
        msgDTO.setContentType(message.getContentType());
        msgDTO.setMsgContent(message.getMsgContent());
        msgDTO.setCompleteMsg(message.getCompleteMsg());
        msgDTO.setXyGoodsId(message.getXyGoodsId());
        msgDTO.setReminderUrl(message.getReminderUrl());
        msgDTO.setSenderUserName(message.getSenderUserName());
        msgDTO.setSenderUserId(message.getSenderUserId());
        msgDTO.setMessageTime(message.getMessageTime());
        richMessageParser.enrich(msgDTO, message);
        return msgDTO;
    }

    private void enrichPeer(OnlineConversationDTO conversation, XianyuChatMessage message, String currentAccountUnb, ConversationEnrichCache cache) {
        String senderUserId = message.getSenderUserId();
        if (senderUserId == null || senderUserId.isBlank() || senderUserId.equals(currentAccountUnb)) {
            return;
        }
        if (hasText(conversation.getPeerUserId()) && !senderUserId.equals(conversation.getPeerUserId())) {
            return;
        }
        conversation.setPeerUserId(senderUserId);
        String peerName = normalizePeerName(message.getSenderUserName());
        if (hasText(peerName) && !hasStablePeerName(conversation)) {
            conversation.setPeerUserName(peerName);
        }
        if (!hasText(conversation.getPeerAvatar())) {
            conversation.setPeerAvatar(pickFirstText(message.getCompleteMsg(), cache, "avatar", "avatarUrl", "senderAvatar", "senderAvatarUrl", "headPic", "headPicUrl"));
        }
    }

    private void enrichGoods(OnlineConversationDTO conversation, XianyuChatMessage message, ConversationEnrichCache cache) {
        if ((conversation.getXyGoodsId() == null || conversation.getXyGoodsId().isBlank())
                && message.getXyGoodsId() != null && !message.getXyGoodsId().isBlank()) {
            conversation.setXyGoodsId(message.getXyGoodsId());
        }
        if (conversation.getXyGoodsId() == null || conversation.getXyGoodsId().isBlank()) {
            return;
        }
        XianyuGoodsInfo goods = findGoods(conversation.getXyGoodsId(), cache);
        if (goods != null) {
            if (conversation.getGoodsTitle() == null || conversation.getGoodsTitle().isBlank()) {
                conversation.setGoodsTitle(goods.getTitle());
            }
            if (conversation.getGoodsCoverPic() == null || conversation.getGoodsCoverPic().isBlank()) {
                conversation.setGoodsCoverPic(goods.getCoverPic());
            }
            if (conversation.getGoodsPrice() == null || conversation.getGoodsPrice().isBlank()) {
                conversation.setGoodsPrice(goods.getSoldPrice());
            }
            if (conversation.getGoodsStatus() == null) {
                conversation.setGoodsStatus(goods.getStatus());
            }
        }
    }

    private void enrichOrder(OnlineConversationDTO conversation, XianyuChatMessage message, ConversationEnrichCache cache) {
        XianyuOrder order = needsPrimaryOrderLookup(conversation) ? findOrder(message, cache) : null;
        if (order != null) {
            if (conversation.getOrderId() == null || conversation.getOrderId().isBlank()) {
                conversation.setOrderId(order.getOrderId());
            }
            if (conversation.getOrderStatus() == null) {
                conversation.setOrderStatus(order.getOrderStatus());
            }
            if (conversation.getOrderStatusText() == null || conversation.getOrderStatusText().isBlank()) {
                conversation.setOrderStatusText(order.getOrderStatusText());
            }
            if (conversation.getOrderAmountText() == null || conversation.getOrderAmountText().isBlank()) {
                conversation.setOrderAmountText(order.getOrderAmountText());
            }
            if (conversation.getXyGoodsId() == null || conversation.getXyGoodsId().isBlank()) {
                conversation.setXyGoodsId(order.getXyGoodsId());
            }
            if (conversation.getGoodsTitle() == null || conversation.getGoodsTitle().isBlank()) {
                conversation.setGoodsTitle(order.getGoodsTitle());
            }
            if ((conversation.getPeerUserId() == null || conversation.getPeerUserId().isBlank()) && hasText(order.getBuyerUserId())) {
                conversation.setPeerUserId(order.getBuyerUserId());
            }
            String buyerName = normalizePeerName(order.getBuyerUserName());
            if (hasText(buyerName) && !hasStablePeerName(conversation)) {
                conversation.setPeerUserName(buyerName);
            }
        }
        XianyuGoodsOrder goodsOrder = needsGoodsOrderLookup(conversation) ? findGoodsOrder(message, conversation.getXyGoodsId(), cache) : null;
        if (goodsOrder != null) {
            if (conversation.getAutoDeliveryState() == null) {
                conversation.setAutoDeliveryState(goodsOrder.getState());
            }
            if (conversation.getAutoDeliveryStateText() == null || conversation.getAutoDeliveryStateText().isBlank()) {
                conversation.setAutoDeliveryStateText(autoDeliveryStateText(goodsOrder));
            }
            if (conversation.getOrderId() == null || conversation.getOrderId().isBlank()) {
                conversation.setOrderId(goodsOrder.getOrderId());
            }
            if (conversation.getPeerUserId() == null || conversation.getPeerUserId().isBlank()) {
                conversation.setPeerUserId(goodsOrder.getBuyerUserId());
            }
            String buyerName = normalizePeerName(goodsOrder.getBuyerUserName());
            if (hasText(buyerName) && !hasStablePeerName(conversation)) {
                conversation.setPeerUserName(buyerName);
            }
        }
    }

    private boolean needsPrimaryOrderLookup(OnlineConversationDTO conversation) {
        return conversation.getOrderId() == null || conversation.getOrderId().isBlank()
                || conversation.getOrderStatus() == null
                || conversation.getOrderStatusText() == null || conversation.getOrderStatusText().isBlank()
                || conversation.getOrderAmountText() == null || conversation.getOrderAmountText().isBlank()
                || conversation.getXyGoodsId() == null || conversation.getXyGoodsId().isBlank()
                || conversation.getGoodsTitle() == null || conversation.getGoodsTitle().isBlank();
    }

    private boolean needsGoodsOrderLookup(OnlineConversationDTO conversation) {
        return conversation.getAutoDeliveryState() == null
                || conversation.getAutoDeliveryStateText() == null || conversation.getAutoDeliveryStateText().isBlank()
                || conversation.getOrderId() == null || conversation.getOrderId().isBlank()
                || conversation.getPeerUserId() == null || conversation.getPeerUserId().isBlank()
                || conversation.getPeerUserName() == null || conversation.getPeerUserName().isBlank();
    }

    private void enrichCard(OnlineConversationDTO conversation, XianyuChatMessage message, ConversationEnrichCache cache) {
        String completeMsg = message.getCompleteMsg();
        if (conversation.getCardTitle() == null || conversation.getCardTitle().isBlank()) {
            conversation.setCardTitle(pickFirstText(completeMsg, cache, "title", "mainTitle", "itemTitle", "itemName", "goodsTitle", "subject", "bizTitle"));
        }
        if (conversation.getCardSubtitle() == null || conversation.getCardSubtitle().isBlank()) {
            conversation.setCardSubtitle(pickFirstText(completeMsg, cache, "firstLineText", "subTitle", "subtitle", "desc", "content", "reminderContent", "tip", "price", "statusText", "orderStatus", "bizDesc"));
        }
        if (conversation.getCardImageUrl() == null || conversation.getCardImageUrl().isBlank()) {
            conversation.setCardImageUrl(pickFirstText(completeMsg, cache, "imgUrl", "picUrl", "image", "imageUrl", "cover", "coverPic", "itemPic", "itemImage", "goodsCoverPic"));
        }
        if (conversation.getCardActionText() == null || conversation.getCardActionText().isBlank()) {
            conversation.setCardActionText(pickFirstText(completeMsg, cache, "buttonText", "actionText", "actionName", "btnText", "text"));
        }
        if (conversation.getCardTag() == null || conversation.getCardTag().isBlank()) {
            conversation.setCardTag(pickFirstText(completeMsg, cache, "tag", "cardTypeName", "bizTypeName"));
        }
    }

    private XianyuGoodsInfo findGoods(String xyGoodsId, ConversationEnrichCache cache) {
        if (!hasText(xyGoodsId)) {
            return null;
        }
        return cache.goodsCache.computeIfAbsent(xyGoodsId, this::loadGoods).orElse(null);
    }

    private Optional<XianyuGoodsInfo> loadGoods(String xyGoodsId) {
        try {
            XianyuGoodsInfo goods = goodsInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuGoodsInfo>()
                    .eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId)
                    .last("LIMIT 1"));
            return Optional.ofNullable(goods);
        } catch (Exception e) {
            log.debug("查询会话商品信息失败: xyGoodsId={}", xyGoodsId, e);
            return Optional.empty();
        }
    }

    private XianyuOrder findOrder(XianyuChatMessage message, ConversationEnrichCache cache) {
        if (hasText(message.getSId())) {
            String cacheKey = message.getXianyuAccountId() + ":" + message.getSId();
            return cache.orderBySidCache.computeIfAbsent(cacheKey, key -> loadOrderBySid(message)).orElse(null);
        }
        if (hasText(message.getPnmId())) {
            String cacheKey = message.getXianyuAccountId() + ":" + message.getPnmId();
            return cache.orderByPnmCache.computeIfAbsent(cacheKey, key -> loadOrderByPnm(message)).orElse(null);
        }
        return null;
    }

    private Optional<XianyuOrder> loadOrderBySid(XianyuChatMessage message) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder>()
                    .eq(XianyuOrder::getXianyuAccountId, message.getXianyuAccountId())
                    .eq(XianyuOrder::getSId, message.getSId())
                    .orderByDesc(XianyuOrder::getOrderCreateTime)
                    .last("LIMIT 1");
            return Optional.ofNullable(orderMapper.selectOne(wrapper));
        } catch (Exception e) {
            log.debug("按sid查询会话订单信息失败: sid={}", message.getSId(), e);
            return Optional.empty();
        }
    }

    private Optional<XianyuOrder> loadOrderByPnm(XianyuChatMessage message) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder>()
                    .eq(XianyuOrder::getXianyuAccountId, message.getXianyuAccountId())
                    .eq(XianyuOrder::getPnmId, message.getPnmId())
                    .orderByDesc(XianyuOrder::getOrderCreateTime)
                    .last("LIMIT 1");
            return Optional.ofNullable(orderMapper.selectOne(wrapper));
        } catch (Exception e) {
            log.debug("按pnmId查询会话订单信息失败: pnmId={}", message.getPnmId(), e);
            return Optional.empty();
        }
    }

    private XianyuGoodsOrder findGoodsOrder(XianyuChatMessage message, String xyGoodsId, ConversationEnrichCache cache) {
        if (hasText(xyGoodsId) && hasText(message.getSId())) {
            String cacheKey = message.getXianyuAccountId() + ":" + xyGoodsId + ":" + message.getSId();
            return cache.goodsOrderBySidCache.computeIfAbsent(cacheKey,
                    key -> loadGoodsOrderBySid(message.getXianyuAccountId(), xyGoodsId, message.getSId())).orElse(null);
        }
        if (hasText(message.getPnmId())) {
            String cacheKey = message.getXianyuAccountId() + ":" + message.getPnmId();
            return cache.goodsOrderByPnmCache.computeIfAbsent(cacheKey,
                    key -> loadGoodsOrderByPnm(message.getXianyuAccountId(), message.getPnmId())).orElse(null);
        }
        return null;
    }

    private Optional<XianyuGoodsOrder> loadGoodsOrderBySid(Long accountId, String xyGoodsId, String sid) {
        try {
            return Optional.ofNullable(goodsOrderMapper.selectLatestBySid(accountId, xyGoodsId, sid));
        } catch (Exception e) {
            log.debug("按sid查询会话自动发货订单失败: accountId={}, xyGoodsId={}, sid={}", accountId, xyGoodsId, sid, e);
            return Optional.empty();
        }
    }

    private Optional<XianyuGoodsOrder> loadGoodsOrderByPnm(Long accountId, String pnmId) {
        try {
            return Optional.ofNullable(goodsOrderMapper.selectByPnmId(accountId, pnmId));
        } catch (Exception e) {
            log.debug("按pnmId查询会话自动发货订单失败: accountId={}, pnmId={}", accountId, pnmId, e);
            return Optional.empty();
        }
    }

    private String autoDeliveryStateText(XianyuGoodsOrder order) {
        if (order.getState() == null) {
            return "未发货";
        }
        if (order.getState() == 1) {
            return order.getConfirmState() != null && order.getConfirmState() == 1 ? "已发货" : "已发送";
        }
        if (order.getState() == -1) {
            return "发货失败";
        }
        return "待发货";
    }

    private String pickFirstText(String json, ConversationEnrichCache cache, String... keys) {
        if (json == null || json.isBlank()) {
            return "";
        }
        JsonLookup lookup = cache.jsonLookupCache.computeIfAbsent(json, this::buildJsonLookup);
        return lookup.pickFirstText(keys);
    }

    private JsonLookup buildJsonLookup(String json) {
        List<JsonNode> nodes = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            List<JsonNode> queue = new ArrayList<>();
            queue.add(root);
            int visited = 0;
            while (!queue.isEmpty() && visited < 220) {
                visited++;
                JsonNode node = queue.remove(0);
                if (node == null || node.isNull()) {
                    continue;
                }
                nodes.add(node);
                if (node.isObject()) {
                    Iterator<JsonNode> values = node.elements();
                    while (values.hasNext()) {
                        JsonNode value = values.next();
                        if (value.isTextual()) {
                            JsonNode parsed = parseJsonNode(value.asText());
                            if (parsed != null) {
                                queue.add(parsed);
                            }
                        } else {
                            queue.add(value);
                        }
                    }
                } else if (node.isArray()) {
                    node.forEach(queue::add);
                }
            }
        } catch (Exception e) {
            log.debug("解析会话富消息字段失败", e);
        }
        return new JsonLookup(nodes);
    }

    private JsonNode parseJsonNode(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private static String cleanText(String value) {
        return value.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private boolean isStandaloneNoticeMessage(XianyuChatMessage message) {
        if (message == null || hasText(message.getXyGoodsId())) {
            return false;
        }
        String senderName = cleanNullableText(message.getSenderUserName());
        return Integer.valueOf(28).equals(message.getContentType())
                && STANDALONE_NOTICE_NAMES.contains(senderName);
    }

    private void finalizeConversation(OnlineConversationDTO conversation, ConversationEnrichCache cache) {
        if (!hasText(conversation.getPeerAvatar())) {
            conversation.setPeerAvatar(findNearbyNoticeAvatar(conversation, cache));
        }
        if (hasText(conversation.getPeerUserId()) && !hasStablePeerName(conversation)) {
            conversation.setPeerUserName("买家");
        }
    }

    private void rememberNoticeAvatar(ConversationEnrichCache cache, XianyuChatMessage message) {
        String avatar = pickFirstText(message.getCompleteMsg(), cache,
                "avatar", "avatarUrl", "senderAvatar", "senderAvatarUrl", "headPic", "headPicUrl");
        if (hasText(avatar) && message.getMessageTime() != null) {
            cache.noticeAvatars.add(new NoticeAvatar(message.getMessageTime(), avatar));
        }
    }

    private String findNearbyNoticeAvatar(OnlineConversationDTO conversation, ConversationEnrichCache cache) {
        if (conversation.getLastMessageTime() == null || cache.noticeAvatars.isEmpty()) {
            return "";
        }
        long bestDistance = Long.MAX_VALUE;
        String bestAvatar = "";
        for (NoticeAvatar noticeAvatar : cache.noticeAvatars) {
            long distance = Math.abs(noticeAvatar.messageTime - conversation.getLastMessageTime());
            if (distance < bestDistance && distance <= 300_000L) {
                bestDistance = distance;
                bestAvatar = noticeAvatar.avatarUrl;
            }
        }
        return bestAvatar;
    }

    private boolean hasStablePeerName(OnlineConversationDTO conversation) {
        return hasText(conversation.getPeerUserName())
                && !isSyntheticPeerName(conversation.getPeerUserName());
    }

    private String normalizePeerName(String value) {
        String text = cleanNullableText(value);
        return isSyntheticPeerName(text) ? "" : text;
    }

    private boolean isSyntheticPeerName(String value) {
        String text = cleanNullableText(value);
        return !text.isBlank() && SYNTHETIC_PEER_NAMES.stream().anyMatch(text::contains);
    }

    private String cleanNullableText(String value) {
        return value == null ? "" : cleanText(value);
    }

    private boolean isMessageForAccount(XianyuChatMessage message, Long accountId, String currentAccountUnb) {
        if (message == null) {
            return false;
        }

        boolean hasGoodsId = hasText(message.getXyGoodsId());
        if (hasGoodsId) {
            Boolean goodsOwnedByAccount = isGoodsOwnedByAccount(accountId, message.getXyGoodsId());
            if (Boolean.TRUE.equals(goodsOwnedByAccount)) {
                return true;
            }
            if (Boolean.FALSE.equals(goodsOwnedByAccount)) {
                return false;
            }
        }

        if (!hasText(currentAccountUnb)) {
            return false;
        }
        String receiverUserId = normalizeUserId(extractReceiverUserId(message.getCompleteMsg()));
        String ownUserId = normalizeUserId(currentAccountUnb);
        if (hasText(receiverUserId)) {
            return ownUserId.equals(receiverUserId);
        }

        String senderUserId = normalizeUserId(message.getSenderUserId());
        if (ownUserId.equals(senderUserId)) {
            return true;
        }

        return true;
    }

    private Boolean isGoodsOwnedByAccount(Long accountId, String xyGoodsId) {
        if (accountId == null || !hasText(xyGoodsId) || goodsInfoMapper == null) {
            return null;
        }
        try {
            List<XianyuGoodsInfo> goodsList = goodsInfoMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuGoodsInfo>()
                            .eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId));
            if (goodsList == null || goodsList.isEmpty()) {
                return null;
            }
            return goodsList.stream()
                    .anyMatch(goods -> accountId.equals(goods.getXianyuAccountId()));
        } catch (Exception e) {
            log.warn("查询消息商品归属失败: accountId={}, xyGoodsId={}", accountId, xyGoodsId, e);
            return null;
        }
    }

    private String extractReceiverUserId(String completeMsg) {
        try {
            JsonNode root = parseJsonNode(completeMsg);
            JsonNode receiverNode = root == null ? null : root.path("1").path("10").path("receiver");
            return receiverNode != null && receiverNode.isTextual() ? receiverNode.asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizeUserId(String value) {
        String text = cleanNullableText(value);
        int atIndex = text.indexOf('@');
        return atIndex > 0 ? text.substring(0, atIndex) : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean allConversationsHavePeer(Map<String, OnlineConversationDTO> conversations) {
        return conversations.values().stream()
                .allMatch(item -> item.getPeerUserId() != null && !item.getPeerUserId().isBlank());
    }

    private static final class ConversationEnrichCache {
        private final Map<String, Optional<XianyuGoodsInfo>> goodsCache = new HashMap<>();
        private final Map<String, Optional<XianyuOrder>> orderBySidCache = new HashMap<>();
        private final Map<String, Optional<XianyuOrder>> orderByPnmCache = new HashMap<>();
        private final Map<String, Optional<XianyuGoodsOrder>> goodsOrderBySidCache = new HashMap<>();
        private final Map<String, Optional<XianyuGoodsOrder>> goodsOrderByPnmCache = new HashMap<>();
        private final Map<String, JsonLookup> jsonLookupCache = new HashMap<>();
        private final List<NoticeAvatar> noticeAvatars = new ArrayList<>();
    }

    private static final class NoticeAvatar {
        private final long messageTime;
        private final String avatarUrl;

        private NoticeAvatar(long messageTime, String avatarUrl) {
            this.messageTime = messageTime;
            this.avatarUrl = avatarUrl;
        }
    }

    private static final class JsonLookup {
        private final List<JsonNode> nodes;

        private JsonLookup(List<JsonNode> nodes) {
            this.nodes = nodes;
        }

        private String pickFirstText(String... keys) {
            for (JsonNode node : nodes) {
                for (String key : keys) {
                    JsonNode value = node.get(key);
                    if (value != null && value.isTextual() && !value.asText().isBlank()) {
                        return cleanText(value.asText());
                    }
                }
            }
            return "";
        }
    }
}
