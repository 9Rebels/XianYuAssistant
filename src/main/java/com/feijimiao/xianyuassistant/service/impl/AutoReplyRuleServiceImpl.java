package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleBatchImportReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleQueryReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuAutoReplyRule;
import com.feijimiao.xianyuassistant.mapper.XianyuAutoReplyRuleMapper;
import com.feijimiao.xianyuassistant.service.AutoReplyRuleService;
import com.feijimiao.xianyuassistant.service.bo.AutoReplyRuleMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自动回复规则服务实现
 */
@Slf4j
@Service
public class AutoReplyRuleServiceImpl implements AutoReplyRuleService {

    private static final int MATCH_TYPE_ALL = 2;
    private static final int REPLY_TYPE_IMAGE = 2;
    private static final int REPLY_TYPE_TEXT_IMAGE = 3;

    @Autowired
    private XianyuAutoReplyRuleMapper ruleMapper;

    @Override
    public ResultObject<List<AutoReplyRuleRespDTO>> list(AutoReplyRuleQueryReqDTO reqDTO) {
        LambdaQueryWrapper<XianyuAutoReplyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuAutoReplyRule::getXianyuAccountId, reqDTO.getXianyuAccountId());
        if (Boolean.TRUE.equals(reqDTO.getIncludeGlobal())) {
            wrapper.and(w -> w.eq(XianyuAutoReplyRule::getXyGoodsId, reqDTO.getXyGoodsId())
                    .or()
                    .isNull(XianyuAutoReplyRule::getXyGoodsId)
                    .or()
                    .eq(XianyuAutoReplyRule::getXyGoodsId, ""));
        } else if (!isBlank(reqDTO.getXyGoodsId())) {
            wrapper.eq(XianyuAutoReplyRule::getXyGoodsId, reqDTO.getXyGoodsId());
        }
        wrapper.orderByAsc(XianyuAutoReplyRule::getPriority)
                .orderByDesc(XianyuAutoReplyRule::getId);
        return ResultObject.success(ruleMapper.selectList(wrapper).stream().map(this::toResp).toList());
    }

    @Override
    public ResultObject<AutoReplyRuleRespDTO> save(AutoReplyRuleReqDTO reqDTO) {
        String error = validateRule(reqDTO);
        if (error != null) {
            return ResultObject.failed(error);
        }
        XianyuAutoReplyRule rule = reqDTO.getId() == null
                ? new XianyuAutoReplyRule()
                : ruleMapper.selectById(reqDTO.getId());
        if (rule == null) {
            return ResultObject.failed("自动回复规则不存在");
        }
        copyReq(reqDTO, rule);
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return ResultObject.success(toResp(rule));
    }

    @Override
    public ResultObject<Integer> batchImport(AutoReplyRuleBatchImportReqDTO reqDTO) {
        int count = 0;
        for (AutoReplyRuleReqDTO ruleReq : reqDTO.getRules()) {
            ResultObject<AutoReplyRuleRespDTO> result = save(ruleReq);
            if (result.getCode() == 200) {
                count++;
            } else {
                log.warn("导入自动回复规则跳过: {}", result.getMsg());
            }
        }
        return ResultObject.success(count, "已导入 " + count + " 条规则");
    }

    @Override
    public ResultObject<Void> delete(Long id) {
        if (id == null) {
            return ResultObject.failed("规则ID不能为空");
        }
        ruleMapper.deleteById(id);
        return ResultObject.success(null);
    }

    @Override
    public AutoReplyRuleMatch match(Long accountId, String xyGoodsId, String buyerMessage) {
        if (accountId == null || isBlank(buyerMessage)) {
            return null;
        }
        AutoReplyRuleMatch goodsMatch = matchRuleList(ruleMapper.selectGoodsRules(accountId, xyGoodsId, 0), buyerMessage);
        if (goodsMatch != null) return goodsMatch;
        AutoReplyRuleMatch globalMatch = matchRuleList(ruleMapper.selectGlobalRules(accountId, 0), buyerMessage);
        if (globalMatch != null) return globalMatch;
        AutoReplyRuleMatch goodsDefault = firstDefault(ruleMapper.selectGoodsRules(accountId, xyGoodsId, 1));
        if (goodsDefault != null) return goodsDefault;
        return firstDefault(ruleMapper.selectGlobalRules(accountId, 1));
    }

    private AutoReplyRuleMatch matchRuleList(List<XianyuAutoReplyRule> rules, String buyerMessage) {
        String normalizedMessage = buyerMessage.toLowerCase(Locale.ROOT);
        for (XianyuAutoReplyRule rule : rules) {
            List<String> keywords = splitValues(rule.getKeywords());
            List<String> matched = findMatchedKeywords(keywords, normalizedMessage);
            if (isMatched(rule, keywords, matched)) {
                return toMatch(rule, String.join(",", matched), false);
            }
        }
        return null;
    }

    private AutoReplyRuleMatch firstDefault(List<XianyuAutoReplyRule> rules) {
        for (XianyuAutoReplyRule rule : rules) {
            if (hasReply(rule)) {
                return toMatch(rule, "默认回复", true);
            }
        }
        return null;
    }

    private List<String> findMatchedKeywords(List<String> keywords, String normalizedMessage) {
        List<String> matched = new ArrayList<>();
        for (String keyword : keywords) {
            if (normalizedMessage.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched.add(keyword);
            }
        }
        return matched;
    }

    private boolean isMatched(XianyuAutoReplyRule rule, List<String> keywords, List<String> matched) {
        if (keywords.isEmpty() || !hasReply(rule)) {
            return false;
        }
        return normalizeInt(rule.getMatchType(), 1) == MATCH_TYPE_ALL
                ? matched.size() == keywords.size()
                : !matched.isEmpty();
    }

    private AutoReplyRuleMatch toMatch(XianyuAutoReplyRule rule, String keyword, boolean isDefault) {
        AutoReplyRuleMatch match = new AutoReplyRuleMatch();
        match.setRuleId(rule.getId());
        match.setRuleName(rule.getRuleName());
        match.setMatchedKeyword(keyword);
        match.setReplyType(normalizeInt(rule.getReplyType(), 1));
        match.setReplyContent(rule.getReplyContent());
        match.setImageUrls(splitValues(rule.getImageUrls()));
        match.setDefaultReply(isDefault);
        return match;
    }

    private String validateRule(AutoReplyRuleReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) return "闲鱼账号ID不能为空";
        boolean isDefault = normalizeInt(reqDTO.getIsDefault(), 0) == 1;
        if (!isDefault && isBlank(reqDTO.getKeywords())) return "关键词不能为空";
        int replyType = normalizeInt(reqDTO.getReplyType(), 1);
        if (replyType != REPLY_TYPE_IMAGE && isBlank(reqDTO.getReplyContent())) return "回复内容不能为空";
        if ((replyType == REPLY_TYPE_IMAGE || replyType == REPLY_TYPE_TEXT_IMAGE) && isBlank(reqDTO.getImageUrls())) {
            return "图片URL不能为空";
        }
        return null;
    }

    private boolean hasReply(XianyuAutoReplyRule rule) {
        int replyType = normalizeInt(rule.getReplyType(), 1);
        boolean hasText = !isBlank(rule.getReplyContent());
        boolean hasImage = !splitValues(rule.getImageUrls()).isEmpty();
        return replyType == REPLY_TYPE_IMAGE ? hasImage : hasText || hasImage;
    }

    private void copyReq(AutoReplyRuleReqDTO reqDTO, XianyuAutoReplyRule rule) {
        rule.setXianyuAccountId(reqDTO.getXianyuAccountId());
        rule.setXyGoodsId(blankToNull(reqDTO.getXyGoodsId()));
        rule.setRuleName(reqDTO.getRuleName());
        rule.setKeywords(reqDTO.getKeywords());
        rule.setMatchType(normalizeInt(reqDTO.getMatchType(), 1));
        rule.setReplyType(normalizeInt(reqDTO.getReplyType(), 1));
        rule.setReplyContent(reqDTO.getReplyContent());
        rule.setImageUrls(reqDTO.getImageUrls());
        rule.setPriority(normalizeInt(reqDTO.getPriority(), 100));
        rule.setEnabled(normalizeInt(reqDTO.getEnabled(), 1));
        rule.setIsDefault(normalizeInt(reqDTO.getIsDefault(), 0));
    }

    private AutoReplyRuleRespDTO toResp(XianyuAutoReplyRule rule) {
        AutoReplyRuleRespDTO respDTO = new AutoReplyRuleRespDTO();
        BeanUtils.copyProperties(rule, respDTO);
        return respDTO;
    }

    private List<String> splitValues(String text) {
        if (isBlank(text)) return List.of();
        String[] parts = text.split("[\\n,，;；]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private Integer normalizeInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
