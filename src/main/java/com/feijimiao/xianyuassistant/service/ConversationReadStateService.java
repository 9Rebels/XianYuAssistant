package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuConversationState;

import java.util.List;
import java.util.Map;

public interface ConversationReadStateService {

    void markReadReceipt(Long accountId, String decryptedData);

    void markOutgoingUnread(Long accountId, String sId, Long messageTime);

    Map<String, XianyuConversationState> findStates(Long accountId, List<String> sIds);
}
