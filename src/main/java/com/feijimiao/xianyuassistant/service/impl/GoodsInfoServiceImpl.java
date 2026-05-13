package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.controller.dto.ItemDTO;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import com.feijimiao.xianyuassistant.utils.ItemDetailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 商品信息服务实现类
 */
@Slf4j
@Service
public class GoodsInfoServiceImpl implements GoodsInfoService {

    private static final int DEFAULT_SORT_ORDER = Integer.MAX_VALUE;
    private static final int STATUS_ON_SALE = 0;
    private static final String MESSAGE_GOODS_TITLE_PREFIX = "消息触发商品-";
    private static final String DEFAULT_ORDER_SQL =
            "ORDER BY CASE status WHEN 0 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 3 END ASC, sort_order ASC, id ASC";

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private com.feijimiao.xianyuassistant.mapper.XianyuAutoReplyRuleMapper autoReplyRuleMapper;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTimeString() {
        return DateTimeUtils.currentShanghaiTime();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }

    private LambdaQueryWrapper<XianyuGoodsInfo> buildGoodsQuery(Integer status, Long xianyuAccountId, String keyword) {
        LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(XianyuGoodsInfo::getStatus, status);
        }
        if (xianyuAccountId != null) {
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword != null) {
            queryWrapper.and(wrapper -> wrapper
                    .like(XianyuGoodsInfo::getTitle, normalizedKeyword)
                    .or()
                    .like(XianyuGoodsInfo::getXyGoodId, normalizedKeyword));
        }
        return queryWrapper;
    }

    private void applyDefaultOrdering(LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper) {
        queryWrapper.last(DEFAULT_ORDER_SQL);
    }

    private String buildPagedOrderSql(int offset, int pageSize) {
        return DEFAULT_ORDER_SQL + " LIMIT " + offset + ", " + pageSize;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private XianyuGoodsInfo findByGoodsIdAndAccount(String xyGoodId, Long xianyuAccountId) {
        LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
        if (xianyuAccountId != null) {
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
        }
        return goodsInfoMapper.selectOne(queryWrapper);
    }

    private XianyuGoodsInfo findByGoodsId(String xyGoodId) {
        LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
        return goodsInfoMapper.selectOne(queryWrapper);
    }

    private boolean isPlaceholderTitle(String title, String xyGoodId) {
        return title == null
                || title.trim().isEmpty()
                || title.equals(MESSAGE_GOODS_TITLE_PREFIX + xyGoodId);
    }

    private String resolveItemId(ItemDTO itemDTO) {
        if (itemDTO == null) {
            return null;
        }
        if (itemDTO.getDetailParams() != null && normalizeText(itemDTO.getDetailParams().getItemId()) != null) {
            return itemDTO.getDetailParams().getItemId();
        }
        return itemDTO.getId();
    }

    private String resolveTitle(ItemDTO itemDTO) {
        if (itemDTO == null) {
            return null;
        }
        if (itemDTO.getDetailParams() != null && normalizeText(itemDTO.getDetailParams().getTitle()) != null) {
            return itemDTO.getDetailParams().getTitle();
        }
        return itemDTO.getTitle();
    }

    private String resolveCoverPic(ItemDTO itemDTO) {
        if (itemDTO == null) {
            return null;
        }
        if (itemDTO.getDetailParams() != null && normalizeText(itemDTO.getDetailParams().getPicUrl()) != null) {
            return itemDTO.getDetailParams().getPicUrl();
        }
        return itemDTO.getPicInfo() != null ? itemDTO.getPicInfo().getPicUrl() : null;
    }

    private Integer resolveCount(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    private String resolvePrice(ItemDTO itemDTO) {
        if (itemDTO == null || itemDTO.getPriceInfo() == null) {
            return null;
        }
        return itemDTO.getPriceInfo().getPrice();
    }

    private Integer resolveQuantity(ItemDTO itemDTO) {
        if (itemDTO == null || itemDTO.getQuantity() == null || itemDTO.getQuantity() <= 0) {
            return null;
        }
        return itemDTO.getQuantity();
    }

    private boolean refreshPlaceholderGoods(XianyuGoodsInfo existingGoods, String detailUrl, String titleHint) {
        boolean changed = false;
        String normalizedUrl = normalizeText(detailUrl);
        String normalizedTitle = normalizeText(titleHint);

        if (normalizedUrl != null && normalizeText(existingGoods.getDetailUrl()) == null) {
            existingGoods.setDetailUrl(normalizedUrl);
            changed = true;
        }
        if (normalizedTitle != null && isPlaceholderTitle(existingGoods.getTitle(), existingGoods.getXyGoodId())) {
            existingGoods.setTitle(normalizedTitle);
            changed = true;
        }
        if (changed) {
            existingGoods.setUpdatedTime(getCurrentTimeString());
            goodsInfoMapper.updateById(existingGoods);
        }
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId) {
        return saveOrUpdateGoodsInfo(itemDTO, xianyuAccountId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId, Integer sortOrder) {
        return saveOrUpdateGoodsInfo(itemDTO, xianyuAccountId, sortOrder, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId, Integer sortOrder, Integer forcedStatus) {
        try {
            if (itemDTO == null) {
                log.warn("商品信息为空，跳过保存");
                return false;
            }
            
            String xyGoodId = resolveItemId(itemDTO);
            if (xyGoodId == null || xyGoodId.isEmpty()) {
                log.warn("商品ID为空，跳过保存");
                return false;
            }
            
            // 查询是否已存在
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            XianyuGoodsInfo existingGoods = goodsInfoMapper.selectOne(queryWrapper);
            
            // 构建商品信息
            XianyuGoodsInfo goodsInfo = new XianyuGoodsInfo();
            goodsInfo.setXyGoodId(xyGoodId);
            goodsInfo.setTitle(resolveTitle(itemDTO));
            goodsInfo.setCoverPic(resolveCoverPic(itemDTO));
            
            // 将图片信息JSON数组保存到info_pic字段
            String infoPic = itemDTO.getDetailParams() != null
                    ? itemDTO.getDetailParams().getImageInfos()
                    : null;
            goodsInfo.setInfoPic(infoPic);
            
            // 商品详情页URL
            goodsInfo.setDetailUrl(itemDTO.getDetailUrl());
            
            // 关联闲鱼账号ID
            goodsInfo.setXianyuAccountId(xianyuAccountId);
            
            // 价格信息
            if (itemDTO.getPriceInfo() != null) {
                goodsInfo.setSoldPrice(itemDTO.getPriceInfo().getPrice());
            }

            Integer quantity = resolveQuantity(itemDTO);
            if (quantity != null) {
                goodsInfo.setQuantity(quantity);
            } else if (existingGoods != null) {
                goodsInfo.setQuantity(existingGoods.getQuantity());
            }

            Integer exposureCount = resolveCount(itemDTO.getExposureCount());
            Integer viewCount = resolveCount(itemDTO.getViewCount());
            Integer wantCount = resolveCount(itemDTO.getWantCount());
            goodsInfo.setExposureCount(exposureCount != null ? exposureCount
                    : existingGoods != null ? existingGoods.getExposureCount() : null);
            goodsInfo.setViewCount(viewCount != null ? viewCount
                    : existingGoods != null ? existingGoods.getViewCount() : null);
            goodsInfo.setWantCount(wantCount != null ? wantCount
                    : existingGoods != null ? existingGoods.getWantCount() : null);
            
            goodsInfo.setStatus(forcedStatus != null ? forcedStatus : itemDTO.getItemStatus());
            if (sortOrder != null && sortOrder > 0) {
                goodsInfo.setSortOrder(sortOrder);
            } else if (existingGoods != null && existingGoods.getSortOrder() != null) {
                goodsInfo.setSortOrder(existingGoods.getSortOrder());
            } else {
                goodsInfo.setSortOrder(DEFAULT_SORT_ORDER);
            }
            
            if (existingGoods != null) {
                // 更新现有商品
                goodsInfo.setId(existingGoods.getId());
                goodsInfo.setUpdatedTime(getCurrentTimeString());
                int updated = goodsInfoMapper.updateById(goodsInfo);
                log.info("更新商品信息: xyGoodId={}, title={}, accountId={}, sortOrder={}",
                        xyGoodId, goodsInfo.getTitle(), xianyuAccountId, goodsInfo.getSortOrder());
                return updated > 0;
            } else {
                // 新增商品（ID使用雪花算法自动生成）
                goodsInfo.setCreatedTime(getCurrentTimeString());
                goodsInfo.setUpdatedTime(getCurrentTimeString());
                int inserted = goodsInfoMapper.insert(goodsInfo);
                log.info("新增商品信息: xyGoodId={}, title={}, id={}, accountId={}, sortOrder={}",
                        xyGoodId, goodsInfo.getTitle(), goodsInfo.getId(), xianyuAccountId, goodsInfo.getSortOrder());
                return inserted > 0;
            }
            
        } catch (Exception e) {
            log.error("保存或更新商品信息失败: itemId={}", 
                    itemDTO.getDetailParams() != null ? itemDTO.getDetailParams().getItemId() : "null", e);
            throw new RuntimeException("保存或更新商品信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId) {
        return batchSaveOrUpdateGoodsInfo(itemList, xianyuAccountId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId, Integer startSortOrder) {
        return batchSaveOrUpdateGoodsInfo(itemList, xianyuAccountId, startSortOrder, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId, Integer startSortOrder, Integer forcedStatus) {
        if (itemList == null || itemList.isEmpty()) {
            log.warn("商品列表为空，跳过批量保存");
            return 0;
        }
        
        int successCount = 0;
        for (int index = 0; index < itemList.size(); index++) {
            ItemDTO itemDTO = itemList.get(index);
            try {
                Integer sortOrder = startSortOrder != null ? startSortOrder + index : null;
                if (saveOrUpdateGoodsInfo(itemDTO, xianyuAccountId, sortOrder, forcedStatus)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量保存商品信息时出错: itemId={}", 
                        itemDTO.getDetailParams() != null ? itemDTO.getDetailParams().getItemId() : "null", e);
                // 继续处理下一个商品
            }
        }
        
        log.info("批量保存商品信息完成: 总数={}, 成功={}, accountId={}, startSortOrder={}",
                itemList.size(), successCount, xianyuAccountId, startSortOrder);
        return successCount;
    }

    @Override
    public boolean isGoodsChanged(ItemDTO itemDTO, Long xianyuAccountId, Integer forcedStatus) {
        String xyGoodId = normalizeText(resolveItemId(itemDTO));
        if (xyGoodId == null) {
            return false;
        }

        XianyuGoodsInfo existingGoods = findByGoodsIdAndAccount(xyGoodId, xianyuAccountId);
        if (existingGoods == null) {
            return true;
        }

        Integer targetStatus = forcedStatus != null ? forcedStatus : itemDTO.getItemStatus();
        return !Objects.equals(normalizeText(existingGoods.getTitle()), normalizeText(resolveTitle(itemDTO)))
                || !Objects.equals(normalizeText(existingGoods.getCoverPic()), normalizeText(resolveCoverPic(itemDTO)))
                || !Objects.equals(normalizeText(existingGoods.getDetailUrl()), normalizeText(itemDTO.getDetailUrl()))
                || !Objects.equals(normalizeText(existingGoods.getSoldPrice()), normalizeText(resolvePrice(itemDTO)))
                || quantityChanged(existingGoods.getQuantity(), resolveQuantity(itemDTO))
                || !Objects.equals(existingGoods.getExposureCount(), resolveCount(itemDTO.getExposureCount()))
                || !Objects.equals(existingGoods.getViewCount(), resolveCount(itemDTO.getViewCount()))
                || !Objects.equals(existingGoods.getWantCount(), resolveCount(itemDTO.getWantCount()))
                || !Objects.equals(existingGoods.getStatus(), targetStatus);
    }

    private boolean quantityChanged(Integer oldQuantity, Integer newQuantity) {
        return newQuantity != null && !Objects.equals(oldQuantity, newQuantity);
    }

    @Override
    public Set<String> listGoodsIdsByStatusAndAccountId(Integer status, Long xianyuAccountId) {
        List<XianyuGoodsInfo> goodsList = listByStatusAndAccountId(status, xianyuAccountId);
        Set<String> result = new HashSet<>();
        if (goodsList == null) {
            return result;
        }
        for (XianyuGoodsInfo goods : goodsList) {
            String xyGoodId = normalizeText(goods.getXyGoodId());
            if (xyGoodId != null) {
                result.add(xyGoodId);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int moveMissingGoodsToStatus(Long xianyuAccountId, Integer fromStatus, Set<String> remoteGoodsIds, Integer targetStatus) {
        List<XianyuGoodsInfo> localGoodsList = listByStatusAndAccountId(fromStatus, xianyuAccountId);
        if (localGoodsList == null || localGoodsList.isEmpty()) {
            return 0;
        }
        Set<String> remoteIds = remoteGoodsIds != null ? remoteGoodsIds : Set.of();
        int changed = 0;
        for (XianyuGoodsInfo goods : localGoodsList) {
            if (remoteIds.contains(goods.getXyGoodId())) {
                continue;
            }
            goods.setStatus(targetStatus);
            goods.setUpdatedTime(getCurrentTimeString());
            changed += goodsInfoMapper.updateById(goods) > 0 ? 1 : 0;
        }
        log.info("增量同步移出本地商品状态: accountId={}, fromStatus={}, targetStatus={}, count={}",
                xianyuAccountId, fromStatus, targetStatus, changed);
        return changed;
    }

    @Override
    public XianyuGoodsInfo getByXyGoodId(String xyGoodId) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
            return goodsInfoMapper.selectOne(queryWrapper);
        } catch (Exception e) {
            log.error("根据闲鱼商品ID查询商品信息失败: xyGoodId={}", xyGoodId, e);
            return null;
        }
    }

    @Override
    public XianyuGoodsInfo getByXyGoodIdAndAccountId(Long xianyuAccountId, String xyGoodId) {
        try {
            return findByGoodsIdAndAccount(xyGoodId, xianyuAccountId);
        } catch (Exception e) {
            log.error("根据闲鱼商品ID和账号ID查询商品信息失败: xyGoodId={}, accountId={}",
                    xyGoodId, xianyuAccountId, e);
            return null;
        }
    }

    @Override
    public List<XianyuGoodsInfo> listByStatus(Integer status) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = buildGoodsQuery(status, null, null);
            applyDefaultOrdering(queryWrapper);
            return goodsInfoMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("根据状态查询商品列表失败: status={}", status, e);
            return null;
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = buildGoodsQuery(status, xianyuAccountId, null);
            applyDefaultOrdering(queryWrapper);
            return goodsInfoMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("根据状态和账号ID查询商品列表失败: status={}, accountId={}", status, xianyuAccountId, e);
            return null;
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByStatus(Integer status, int pageNum, int pageSize) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = buildGoodsQuery(status, null, null);
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 使用MyBatis Plus的分页查询
            queryWrapper.last(buildPagedOrderSql(offset, pageSize));
            return goodsInfoMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("根据状态查询商品列表失败: status={}, pageNum={}, pageSize={}", status, pageNum, pageSize, e);
            return new java.util.ArrayList<>(); // 返回空列表而不是null
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId, int pageNum, int pageSize) {
        return listByStatusAndAccountId(status, xianyuAccountId, null, pageNum, pageSize);
    }

    @Override
    public List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId, String keyword, int pageNum, int pageSize) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = buildGoodsQuery(status, xianyuAccountId, keyword);
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 使用MyBatis Plus的分页查询
            queryWrapper.last(buildPagedOrderSql(offset, pageSize));
            return goodsInfoMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("根据状态、账号ID和关键词查询商品列表失败: status={}, accountId={}, keyword={}, pageNum={}, pageSize={}",
                    status, xianyuAccountId, keyword, pageNum, pageSize, e);
            return new java.util.ArrayList<>(); // 返回空列表而不是null
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public XianyuGoodsInfo ensurePlaceholderGoods(Long xianyuAccountId, String xyGoodId, String detailUrl, String titleHint) {
        String normalizedGoodsId = normalizeText(xyGoodId);
        if (normalizedGoodsId == null) {
            return null;
        }

        try {
            XianyuGoodsInfo existingGoods = findByGoodsIdAndAccount(normalizedGoodsId, xianyuAccountId);
            if (existingGoods == null) {
                existingGoods = findByGoodsId(normalizedGoodsId);
            }
            if (existingGoods != null) {
                boolean changed = refreshPlaceholderGoods(existingGoods, detailUrl, titleHint);
                if (changed) {
                    log.info("更新消息触发商品占位信息: xyGoodId={}, accountId={}", normalizedGoodsId, xianyuAccountId);
                }
                return existingGoods;
            }

            String now = getCurrentTimeString();
            XianyuGoodsInfo goodsInfo = new XianyuGoodsInfo();
            goodsInfo.setXyGoodId(normalizedGoodsId);
            goodsInfo.setXianyuAccountId(xianyuAccountId);
            goodsInfo.setTitle(normalizeText(titleHint) != null ? normalizeText(titleHint) : MESSAGE_GOODS_TITLE_PREFIX + normalizedGoodsId);
            goodsInfo.setDetailUrl(normalizeText(detailUrl));
            goodsInfo.setStatus(STATUS_ON_SALE);
            goodsInfo.setSortOrder(DEFAULT_SORT_ORDER);
            goodsInfo.setCreatedTime(now);
            goodsInfo.setUpdatedTime(now);

            goodsInfoMapper.insert(goodsInfo);
            log.info("新增消息触发商品占位信息: xyGoodId={}, id={}, accountId={}",
                    normalizedGoodsId, goodsInfo.getId(), xianyuAccountId);
            return goodsInfo;
        } catch (Exception e) {
            log.error("确保消息触发商品占位信息失败: xyGoodId={}, accountId={}", normalizedGoodsId, xianyuAccountId, e);
            throw new RuntimeException("确保消息触发商品占位信息失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int countByStatusAndAccountId(Integer status, Long xianyuAccountId) {
        return countByStatusAndAccountId(status, xianyuAccountId, null);
    }

    @Override
    public int countByStatusAndAccountId(Integer status, Long xianyuAccountId, String keyword) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = buildGoodsQuery(status, xianyuAccountId, keyword);
            return Math.toIntExact(goodsInfoMapper.selectCount(queryWrapper));
        } catch (Exception e) {
            log.error("根据状态、账号ID和关键词统计商品数量失败: status={}, accountId={}, keyword={}", status, xianyuAccountId, keyword, e);
            return 0;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDetailInfo(String xyGoodId, String detailInfo) {
        try {
            // 提取desc字段
            String extractedDesc = ItemDetailUtils.extractDescFromDetailJson(detailInfo);
            
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
            XianyuGoodsInfo existingGoods = goodsInfoMapper.selectOne(queryWrapper);
            
            if (existingGoods == null) {
                log.warn("商品不存在，无法更新详情: xyGoodId={}", xyGoodId);
                return false;
            }
            
            existingGoods.setDetailInfo(extractedDesc);
            existingGoods.setUpdatedTime(getCurrentTimeString());
            int updated = goodsInfoMapper.updateById(existingGoods);
            
            log.info("更新商品详情成功: xyGoodId={}, 原始详情长度={}, 提取后长度={}", 
                    xyGoodId, detailInfo != null ? detailInfo.length() : 0, 
                    extractedDesc != null ? extractedDesc.length() : 0);
            return updated > 0;
        } catch (Exception e) {
            log.error("更新商品详情失败: xyGoodId={}", xyGoodId, e);
            throw new RuntimeException("更新商品详情失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getDetailInfoByGoodsId(String xyGoodId) {
        LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
        XianyuGoodsInfo goods = goodsInfoMapper.selectOne(queryWrapper);
        return goods != null ? goods.getDetailInfo() : null;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteGoodsInfo(Long xianyuAccountId, String xyGoodId) {
        try {
            // 查询商品信息
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            XianyuGoodsInfo existingGoods = goodsInfoMapper.selectOne(queryWrapper);
            
            if (existingGoods == null) {
                log.warn("商品不存在，无法删除: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId);
                return false;
            }
            
            // 删除商品
            int deleted = goodsInfoMapper.deleteById(existingGoods.getId());
            
            log.info("删除商品成功: xyGoodId={}, title={}, id={}, accountId={}", 
                    xyGoodId, existingGoods.getTitle(), existingGoods.getId(), xianyuAccountId);
            return deleted > 0;
        } catch (Exception e) {
            log.error("删除商品失败: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId, e);
            throw new RuntimeException("删除商品失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateGoodsStatus(Long xianyuAccountId, String xyGoodId, Integer status) {
        try {
            XianyuGoodsInfo existingGoods = findByGoodsIdAndAccount(xyGoodId, xianyuAccountId);
            if (existingGoods == null) {
                log.warn("商品不存在，无法更新状态: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId);
                return false;
            }

            existingGoods.setStatus(status);
            existingGoods.setUpdatedTime(getCurrentTimeString());
            int updated = goodsInfoMapper.updateById(existingGoods);

            log.info("更新商品状态成功: xyGoodId={}, accountId={}, status={}",
                    xyGoodId, xianyuAccountId, status);
            return updated > 0;
        } catch (Exception e) {
            log.error("更新商品状态失败: xyGoodId={}, accountId={}, status={}",
                    xyGoodId, xianyuAccountId, status, e);
            throw new RuntimeException("更新商品状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateGoodsPrice(Long xianyuAccountId, String xyGoodId, String price) {
        try {
            XianyuGoodsInfo existingGoods = findByGoodsIdAndAccount(xyGoodId, xianyuAccountId);
            if (existingGoods == null) {
                log.warn("商品不存在，无法更新价格: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId);
                return false;
            }

            existingGoods.setSoldPrice(price);
            existingGoods.setUpdatedTime(getCurrentTimeString());
            int updated = goodsInfoMapper.updateById(existingGoods);

            log.info("更新商品价格成功: xyGoodId={}, accountId={}, price={}",
                    xyGoodId, xianyuAccountId, price);
            return updated > 0;
        } catch (Exception e) {
            log.error("更新商品价格失败: xyGoodId={}, accountId={}, price={}",
                    xyGoodId, xianyuAccountId, price, e);
            throw new RuntimeException("更新商品价格失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateGoodsQuantity(Long xianyuAccountId, String xyGoodId, Integer quantity) {
        try {
            if (quantity == null || quantity <= 0) {
                log.warn("库存非法，无法更新库存: xyGoodId={}, accountId={}, quantity={}",
                        xyGoodId, xianyuAccountId, quantity);
                return false;
            }

            XianyuGoodsInfo existingGoods = findByGoodsIdAndAccount(xyGoodId, xianyuAccountId);
            if (existingGoods == null) {
                log.warn("商品不存在，无法更新库存: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId);
                return false;
            }

            existingGoods.setQuantity(quantity);
            existingGoods.setUpdatedTime(getCurrentTimeString());
            int updated = goodsInfoMapper.updateById(existingGoods);

            log.info("更新商品库存成功: xyGoodId={}, accountId={}, quantity={}",
                    xyGoodId, xianyuAccountId, quantity);
            return updated > 0;
        } catch (Exception e) {
            log.error("更新商品库存失败: xyGoodId={}, accountId={}, quantity={}",
                    xyGoodId, xianyuAccountId, quantity, e);
            throw new RuntimeException("更新商品库存失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceGoodsId(Long xianyuAccountId, String oldXyGoodId, String newXyGoodId,
                                  String detailUrl, Integer status) {
        try {
            if (normalizeText(oldXyGoodId) == null || normalizeText(newXyGoodId) == null) {
                return false;
            }
            int updated = goodsInfoMapper.replaceGoodsId(
                    xianyuAccountId, oldXyGoodId, newXyGoodId, detailUrl, status);
            if (updated <= 0) {
                return false;
            }
            goodsConfigMapper.replaceGoodsId(xianyuAccountId, oldXyGoodId, newXyGoodId);
            autoDeliveryConfigMapper.replaceGoodsId(xianyuAccountId, oldXyGoodId, newXyGoodId);
            autoReplyRuleMapper.replaceGoodsId(xianyuAccountId, oldXyGoodId, newXyGoodId);
            log.info("替换本地商品ID成功: accountId={}, oldXyGoodId={}, newXyGoodId={}",
                    xianyuAccountId, oldXyGoodId, newXyGoodId);
            return true;
        } catch (Exception e) {
            log.error("替换本地商品ID失败: accountId={}, oldXyGoodId={}, newXyGoodId={}",
                    xianyuAccountId, oldXyGoodId, newXyGoodId, e);
            throw new RuntimeException("替换本地商品ID失败: " + e.getMessage(), e);
        }
    }
}
