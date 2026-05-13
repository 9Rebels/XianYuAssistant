package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.controller.dto.ItemDTO;

import java.util.List;
import java.util.Set;

/**
 * 商品信息服务接口
 */
public interface GoodsInfoService {
    
    /**
     * 保存或更新商品信息
     *
     * @param itemDTO 商品信息DTO
     * @param xianyuAccountId 闲鱼账号ID
     * @return 是否保存成功
     */
    boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId);

    /**
     * 保存或更新商品信息，并记录闲鱼客户端列表顺序
     *
     * @param itemDTO 商品信息DTO
     * @param xianyuAccountId 闲鱼账号ID
     * @param sortOrder 闲鱼客户端列表顺序，数值越小越靠前
     * @return 是否保存成功
     */
    boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId, Integer sortOrder);

    /**
     * 保存或更新商品信息，并强制写入本地商品状态。
     *
     * @param itemDTO 商品信息DTO
     * @param xianyuAccountId 闲鱼账号ID
     * @param sortOrder 闲鱼客户端列表顺序，数值越小越靠前
     * @param forcedStatus 本地商品状态，0=在售，2=已售出
     * @return 是否保存成功
     */
    boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId, Integer sortOrder, Integer forcedStatus);

    /**
     * 保存前判断本地商品是否需要新增或更新。
     */
    boolean isGoodsChanged(ItemDTO itemDTO, Long xianyuAccountId, Integer forcedStatus);

    /**
     * 查询指定账号和状态下的商品ID集合。
     */
    Set<String> listGoodsIdsByStatusAndAccountId(Integer status, Long xianyuAccountId);

    /**
     * 将本地仍处于指定状态、但本次接口没有返回的商品移出该状态。
     */
    int moveMissingGoodsToStatus(Long xianyuAccountId, Integer fromStatus, Set<String> remoteGoodsIds, Integer targetStatus);
    
    /**
     * 批量保存或更新商品信息
     *
     * @param itemList 商品信息列表
     * @param xianyuAccountId 闲鱼账号ID
     * @return 成功保存的商品数量
     */
    int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId);

    /**
     * 批量保存或更新商品信息，并从指定顺序开始记录闲鱼客户端列表顺序
     *
     * @param itemList 商品信息列表
     * @param xianyuAccountId 闲鱼账号ID
     * @param startSortOrder 起始顺序，通常为页偏移 + 1
     * @return 成功保存的商品数量
     */
    int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId, Integer startSortOrder);

    /**
     * 批量保存或更新商品信息，并强制写入本地商品状态。
     *
     * @param itemList 商品信息列表
     * @param xianyuAccountId 闲鱼账号ID
     * @param startSortOrder 起始顺序，通常为页偏移 + 1
     * @param forcedStatus 本地商品状态，0=在售，2=已售出
     * @return 成功保存的商品数量
     */
    int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId, Integer startSortOrder, Integer forcedStatus);
    
    /**
     * 根据闲鱼商品ID获取商品信息
     *
     * @param xyGoodId 闲鱼商品ID
     * @return 商品信息
     */
    XianyuGoodsInfo getByXyGoodId(String xyGoodId);

    /**
     * 根据商品ID和账号ID获取商品信息
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodId 闲鱼商品ID
     * @return 商品信息
     */
    XianyuGoodsInfo getByXyGoodIdAndAccountId(Long xianyuAccountId, String xyGoodId);

    /**
     * 确保消息中解析到的商品已沉淀到本地商品表。
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodId 闲鱼商品ID
     * @param detailUrl 商品详情页URL
     * @param titleHint 商品标题提示
     * @return 已存在或新创建的商品信息
     */
    XianyuGoodsInfo ensurePlaceholderGoods(Long xianyuAccountId, String xyGoodId, String detailUrl, String titleHint);
    
    /**
     * 根据状态查询商品列表
     *
     * @param status 商品状态
     * @return 商品列表
     */
    List<XianyuGoodsInfo> listByStatus(Integer status);
    
    /**
     * 根据状态和账号ID查询商品列表
     *
     * @param status 商品状态
     * @param xianyuAccountId 闲鱼账号ID
     * @return 商品列表
     */
    List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId);
    
    /**
     * 根据状态查询商品列表（分页）
     *
     * @param status 商品状态
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 商品列表
     */
    List<XianyuGoodsInfo> listByStatus(Integer status, int pageNum, int pageSize);
    
    /**
     * 根据状态和账号ID查询商品列表（分页）
     *
     * @param status 商品状态
     * @param xianyuAccountId 闲鱼账号ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 商品列表
     */
    List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId, int pageNum, int pageSize);

    /**
     * 根据状态、账号ID和关键词查询商品列表（分页）
     *
     * @param status 商品状态，为空查询全部状态
     * @param xianyuAccountId 闲鱼账号ID
     * @param keyword 搜索关键词，匹配商品标题和闲鱼商品ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 商品列表
     */
    List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId, String keyword, int pageNum, int pageSize);
    
    /**
     * 根据状态和账号ID统计商品数量
     *
     * @param status 商品状态
     * @param xianyuAccountId 闲鱼账号ID
     * @return 商品数量
     */
    int countByStatusAndAccountId(Integer status, Long xianyuAccountId);

    /**
     * 根据状态、账号ID和关键词统计商品数量
     *
     * @param status 商品状态，为空查询全部状态
     * @param xianyuAccountId 闲鱼账号ID
     * @param keyword 搜索关键词，匹配商品标题和闲鱼商品ID
     * @return 商品数量
     */
    int countByStatusAndAccountId(Integer status, Long xianyuAccountId, String keyword);
    
    /**
     * 更新商品详情信息
     *
     * @param xyGoodId 闲鱼商品ID
     * @param detailInfo 商品详情信息
     * @return 是否更新成功
     */
    boolean updateDetailInfo(String xyGoodId, String detailInfo);
    
    /**
     * 根据商品ID获取商品详情信息
     *
     * @param xyGoodId 闲鱼商品ID
     * @return 商品详情信息
     */
    String getDetailInfoByGoodsId(String xyGoodId);
    
    /**
     * 删除商品信息
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodId 闲鱼商品ID
     * @return 是否删除成功
     */
    boolean deleteGoodsInfo(Long xianyuAccountId, String xyGoodId);

    /**
     * 更新商品状态
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodId 闲鱼商品ID
     * @param status 商品状态
     * @return 是否更新成功
     */
    boolean updateGoodsStatus(Long xianyuAccountId, String xyGoodId, Integer status);

    /**
     * 更新商品价格
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodId 闲鱼商品ID
     * @param price 商品价格
     * @return 是否更新成功
     */
    boolean updateGoodsPrice(Long xianyuAccountId, String xyGoodId, String price);

    /**
     * 更新商品库存
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodId 闲鱼商品ID
     * @param quantity 库存，必须大于0
     * @return 是否更新成功
     */
    boolean updateGoodsQuantity(Long xianyuAccountId, String xyGoodId, Integer quantity);

    /**
     * 重新发布成功后替换本地商品ID，并同步配置表里的商品ID。
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param oldXyGoodId 旧闲鱼商品ID
     * @param newXyGoodId 新闲鱼商品ID
     * @param detailUrl 新商品详情地址
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean replaceGoodsId(Long xianyuAccountId, String oldXyGoodId, String newXyGoodId,
                           String detailUrl, Integer status);
}
