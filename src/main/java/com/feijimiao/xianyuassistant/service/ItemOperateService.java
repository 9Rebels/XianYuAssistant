package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.controller.dto.ItemListRespDTO;

public interface ItemOperateService {
    ItemListRespDTO getSellerItemList(String cookie, int pageNumber, int pageSize, int syncStatus);

    void offShelfItem(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo);

    void upShelfItem(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo);

    void deleteItem(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo);

    void updatePrice(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo, String price);

    void updateQuantity(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo, Integer quantity);
}
