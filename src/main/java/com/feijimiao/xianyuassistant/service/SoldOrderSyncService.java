package com.feijimiao.xianyuassistant.service;

import lombok.Data;

public interface SoldOrderSyncService {
    String NON_FISH_SHOP_MESSAGE = "当前账号不是鱼小铺，无法同步卖家订单列表";

    SyncResult syncSoldOrders(Long accountId);

    @Data
    class SyncResult {
        private Integer totalCount;
        private Integer fetchedCount = 0;
        private Integer insertedCount = 0;
        private Integer updatedCount = 0;
        private Integer pageCount = 0;
        private Boolean nextPage = false;
        private String lastEndRow;
    }
}
