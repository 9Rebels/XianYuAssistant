package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.AllItemsReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.DeleteItemRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.DeleteItemReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemListRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.RagAutoReplyConfigRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.UpdateRagAutoReplyConfigReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.RefreshItemsRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.UpdateItemPriceReqDTO;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.service.ItemDetailSyncService;
import com.feijimiao.xianyuassistant.service.ItemOperateService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import com.feijimiao.xianyuassistant.service.bo.SaveSettingReqBO;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ItemServiceImplTest {
    private static final String OLD_COOKIE = "unb=10001; _m_h5_tk=oldtoken_1999999999999; cookie2=abc";
    private static final String NEW_COOKIE = "unb=10001; _m_h5_tk=newtoken_1999999999999; cookie2=abc";

    @Test
    void soldSyncForcesLocalSoldStatus() {
        assertEquals(2, ItemServiceImpl.normalizeItemStatusForSync(2, 1));
    }

    @Test
    void onSaleSyncForcesLocalOnSaleStatus() {
        assertEquals(0, ItemServiceImpl.normalizeItemStatusForSync(0, 1));
    }

    @Test
    void refreshItemsRecoversExpiredTokenAndRetriesCurrentPage() {
        ItemServiceImpl service = new ItemServiceImpl();
        AccountService accountService = mock(AccountService.class);
        GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);
        XianyuApiRecoveryService apiRecoveryService = mock(XianyuApiRecoveryService.class);
        ItemDetailSyncService detailSyncService = mock(ItemDetailSyncService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "goodsInfoService", goodsInfoService);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", apiRecoveryService);
        ReflectionTestUtils.setField(service, "itemDetailSyncService", detailSyncService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        when(accountService.getCookieByAccountId(2L)).thenReturn(OLD_COOKIE);
        when(accountMapper.selectById(2L)).thenReturn(account(2L, false));
        when(apiRecoveryService.callApi(any(XianyuApiRecoveryRequest.class)))
                .thenReturn(apiSuccess(successfulGroupResponse(), OLD_COOKIE, false))
                .thenReturn(apiSuccess(successfulListResponse(), NEW_COOKIE, true));
        when(goodsInfoService.saveOrUpdateGoodsInfo(any(), anyLong(), anyInt(), anyInt())).thenReturn(true);
        when(detailSyncService.startSync(anyLong(), any())).thenReturn("sync-1");

        ResultObject<RefreshItemsRespDTO> result = service.refreshItems(refreshReq());

        assertEquals(200, result.getCode());
        assertTrue(result.getData().getSuccess());
        assertEquals(1, result.getData().getTotalCount());
        verify(apiRecoveryService, times(2)).callApi(any(XianyuApiRecoveryRequest.class));
    }

    @Test
    void refreshItemsReturnsCaptchaRecoveryInfoWhenAutoRecoveryFails() {
        ItemServiceImpl service = new ItemServiceImpl();
        AccountService accountService = mock(AccountService.class);
        XianyuApiRecoveryService apiRecoveryService = mock(XianyuApiRecoveryService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", apiRecoveryService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        when(accountService.getCookieByAccountId(2L)).thenReturn(OLD_COOKIE);
        when(accountMapper.selectById(2L)).thenReturn(account(2L, false));
        CookieRecoveryResult recovery = CookieRecoveryResult.failed("自动滑块失败，请人工更新 Cookie");
        recovery.setNeedCaptcha(true);
        when(apiRecoveryService.callApi(any(XianyuApiRecoveryRequest.class)))
                .thenReturn(apiSuccess(successfulGroupResponse(), OLD_COOKIE, false))
                .thenReturn(XianyuApiRecoveryResult.failed(null, recovery.getMessage(), recovery));

        ResultObject<RefreshItemsRespDTO> result = service.refreshItems(refreshReq());

        assertEquals(200, result.getCode());
        assertFalse(result.getData().getSuccess());
        assertTrue(result.getData().getRecoveryAttempted());
        assertTrue(result.getData().getNeedCaptcha());
        assertEquals("自动滑块失败，请人工更新 Cookie", result.getData().getMessage());
        assertEquals(null, result.getData().getManualVerifyUrl());
    }

    @Test
    void refreshItemsUsesSellerWorkbenchListForFishShopAccount() {
        ItemServiceImpl service = new ItemServiceImpl();
        AccountService accountService = mock(AccountService.class);
        GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);
        XianyuApiRecoveryService apiRecoveryService = mock(XianyuApiRecoveryService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "goodsInfoService", goodsInfoService);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", apiRecoveryService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        when(accountService.getCookieByAccountId(2L)).thenReturn(OLD_COOKIE);
        when(accountMapper.selectById(2L)).thenReturn(account(2L, true));
        when(apiRecoveryService.callApi(any(XianyuApiRecoveryRequest.class)))
                .thenReturn(apiSuccess(successfulGroupResponse(), OLD_COOKIE, false))
                .thenReturn(apiSuccess(successfulListResponse(), NEW_COOKIE, true));

        ResultObject<RefreshItemsRespDTO> result = service.refreshItems(refreshReq());

        assertEquals(200, result.getCode());
        assertTrue(result.getData().getSuccess());
        assertEquals(1, result.getData().getTotalCount());
        verify(apiRecoveryService, times(2)).callApi(any(XianyuApiRecoveryRequest.class));
    }

    @Test
    void parseItemListResponseExtractsWantCountFromLabels() {
        ItemServiceImpl service = new ItemServiceImpl();
        XianyuApiRecoveryService apiRecoveryService = mock(XianyuApiRecoveryService.class);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", apiRecoveryService);

        ItemListRespDTO resp = invokeParseItemListResponse(service, """
                {
                  "ret":["SUCCESS::调用成功"],
                  "data":{
                    "cardList":[
                      {
                        "cardData":{
                          "id":"item-1",
                          "title":"测试商品",
                          "itemStatus":0,
                          "detailParams":{
                            "itemId":"item-1",
                            "title":"测试商品",
                            "picUrl":"http://example.com/a.jpg",
                            "soldPrice":"34"
                          },
                          "itemLabelDataVO":{
                            "labelData":{
                              "r3":{
                                "tagList":[
                                  {
                                    "data":{
                                      "content":"14人想要"
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        }
                      }
                    ]
                  }
                }
                """);

        assertTrue(resp.getSuccess());
        assertEquals(1, resp.getItems().size());
        ItemDTO item = resp.getItems().get(0);
        assertEquals(14, item.getWantCount());
        assertEquals("http://example.com/a.jpg", item.getDetailParams().getPicUrl());
    }

    @SuppressWarnings("unchecked")
    private ItemListRespDTO invokeParseItemListResponse(ItemServiceImpl service, String response) {
        try {
            java.lang.reflect.Method method = ItemServiceImpl.class.getDeclaredMethod(
                    "parseItemListResponse", java.util.Map.class, int.class, int.class);
            method.setAccessible(true);
            java.util.Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response, java.util.Map.class);
            return (ItemListRespDTO) method.invoke(service, map, 1, 20);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void updateRagAutoReplyConfigSavesAccountGlobalConfigWithoutGoodsId() {
        ItemServiceImpl service = new ItemServiceImpl();
        SysSettingService sysSettingService = mock(SysSettingService.class);
        XianyuGoodsAutoDeliveryConfigMapper configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
        ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);

        UpdateRagAutoReplyConfigReqDTO reqDTO = new UpdateRagAutoReplyConfigReqDTO();
        reqDTO.setXianyuAccountId(2L);
        reqDTO.setGlobalAiReplyEnabled(true);
        reqDTO.setGlobalAiReplyTemplate("通用话术");
        when(sysSettingService.getSettingValue("global_ai_reply_enabled_2")).thenReturn("1");
        when(sysSettingService.getSettingValue("global_ai_reply_template_2")).thenReturn("通用话术");

        ResultObject<?> result = service.updateRagAutoReplyConfig(reqDTO);

        assertEquals(200, result.getCode());
        RagAutoReplyConfigRespDTO data = (RagAutoReplyConfigRespDTO) result.getData();
        assertTrue(data.getGlobalAiReplyEnabled());
        assertEquals("通用话术", data.getGlobalAiReplyTemplate());
        verify(configMapper, never()).findByAccountIdAndGoodsId(anyLong(), any());
        verify(configMapper, never()).insert(any());
        verify(sysSettingService).saveSetting(argThat((SaveSettingReqBO req) ->
                "global_ai_reply_enabled_2".equals(req.getSettingKey()) && "1".equals(req.getSettingValue())));
        verify(sysSettingService).saveSetting(argThat((SaveSettingReqBO req) ->
                "global_ai_reply_template_2".equals(req.getSettingKey()) && "通用话术".equals(req.getSettingValue())));
    }

    @Test
    void updateItemPriceRejectsNormalAccountBeforeRemoteCall() {
        ItemServiceImpl service = new ItemServiceImpl();
        GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ItemOperateService itemOperateService = mock(ItemOperateService.class);
        ReflectionTestUtils.setField(service, "goodsInfoService", goodsInfoService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "itemOperateService", itemOperateService);

        when(goodsInfoService.getByXyGoodIdAndAccountId(2L, "item-1")).thenReturn(goods(2L, "item-1"));
        XianyuAccount account = account(2L, false);
        when(accountMapper.selectById(2L)).thenReturn(account);

        ResultObject<String> result = service.updateItemPrice(updatePriceReq());

        assertEquals(500, result.getCode());
        assertEquals("当前账号不是鱼小铺，无法改价", result.getMsg());
        verifyNoInteractions(itemOperateService);
    }

    @Test
    void offShelfFeatureSwitchRejectsBeforeRemoteCall() {
        ItemServiceImpl service = new ItemServiceImpl();
        SysSettingService sysSettingService = mock(SysSettingService.class);
        ItemOperateService itemOperateService = mock(ItemOperateService.class);
        ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
        ReflectionTestUtils.setField(service, "itemOperateService", itemOperateService);
        when(sysSettingService.getSettingValue("goods_off_shelf_enabled")).thenReturn("0");

        ResultObject<String> result = service.offShelfItem(deleteReq());

        assertEquals(500, result.getCode());
        assertEquals("商品下架功能已关闭", result.getMsg());
        verifyNoInteractions(itemOperateService);
    }

    @Test
    void deleteFeatureSwitchRejectsBeforeRemoteCall() {
        ItemServiceImpl service = new ItemServiceImpl();
        SysSettingService sysSettingService = mock(SysSettingService.class);
        ItemOperateService itemOperateService = mock(ItemOperateService.class);
        ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
        ReflectionTestUtils.setField(service, "itemOperateService", itemOperateService);
        when(sysSettingService.getSettingValue("goods_delete_enabled")).thenReturn("0");

        ResultObject<String> result = service.remoteDeleteItem(deleteReq());

        assertEquals(500, result.getCode());
        assertEquals("闲鱼删除功能已关闭", result.getMsg());
        verifyNoInteractions(itemOperateService);
    }

    @Test
    void republishItemRestoresOriginalItemStatus() {
        ItemServiceImpl service = new ItemServiceImpl();
        SysSettingService sysSettingService = mock(SysSettingService.class);
        GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        AccountService accountService = mock(AccountService.class);
        ItemOperateService itemOperateService = mock(ItemOperateService.class);
        ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
        ReflectionTestUtils.setField(service, "goodsInfoService", goodsInfoService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "itemOperateService", itemOperateService);

        XianyuGoodsInfo goods = goods(2L, "item-1");
        goods.setStatus(1);
        XianyuAccount account = account(2L, false);
        when(sysSettingService.getSettingValue("goods_off_shelf_enabled")).thenReturn("1");
        when(goodsInfoService.getByXyGoodIdAndAccountId(2L, "item-1")).thenReturn(goods);
        when(accountMapper.selectById(2L)).thenReturn(account);
        when(accountService.getCookieByAccountId(2L)).thenReturn("cookie");
        when(goodsInfoService.updateGoodsStatus(2L, "item-1", 0)).thenReturn(true);

        ResultObject<String> result = service.republishItem(deleteReq());

        assertEquals(200, result.getCode());
        assertEquals("商品已恢复在售", result.getData());
        verify(itemOperateService).upShelfItem(account, "cookie", goods);
        verify(goodsInfoService).updateGoodsStatus(2L, "item-1", 0);
        verify(goodsInfoService, never()).replaceGoodsId(anyLong(), any(), any(), any(), any());
    }

    private XianyuApiRecoveryResult apiSuccess(String response, String cookie, boolean recovered) {
        return XianyuApiRecoveryResult.success(response, cookie, recovered, null);
    }

    private AllItemsReqDTO refreshReq() {
        AllItemsReqDTO reqDTO = new AllItemsReqDTO();
        reqDTO.setXianyuAccountId(2L);
        reqDTO.setPageSize(20);
        reqDTO.setMaxPages(1);
        reqDTO.setSyncStatus(0);
        return reqDTO;
    }

    private UpdateItemPriceReqDTO updatePriceReq() {
        UpdateItemPriceReqDTO reqDTO = new UpdateItemPriceReqDTO();
        reqDTO.setXianyuAccountId(2L);
        reqDTO.setXyGoodsId("item-1");
        reqDTO.setPrice("12.50");
        return reqDTO;
    }

    private DeleteItemReqDTO deleteReq() {
        DeleteItemReqDTO reqDTO = new DeleteItemReqDTO();
        reqDTO.setXianyuAccountId(2L);
        reqDTO.setXyGoodsId("item-1");
        return reqDTO;
    }

    private XianyuGoodsInfo goods(Long accountId, String xyGoodsId) {
        XianyuGoodsInfo goods = new XianyuGoodsInfo();
        goods.setXianyuAccountId(accountId);
        goods.setXyGoodId(xyGoodsId);
        goods.setStatus(0);
        goods.setSoldPrice("10.00");
        return goods;
    }

    private XianyuAccount account(Long accountId, boolean fishShopUser) {
        XianyuAccount account = new XianyuAccount();
        account.setId(accountId);
        account.setFishShopUser(fishShopUser);
        return account;
    }

    private String successfulListResponse() {
        return """
                {
                  "ret":["SUCCESS::调用成功"],
                  "data":{
                    "cardList":[
                      {
                        "cardData":{
                          "id":"item-1",
                          "title":"测试商品",
                          "itemStatus":0,
                          "detailParams":{"itemId":"item-1","title":"测试商品","soldPrice":"1.00"}
                        }
                      }
                    ]
                  }
                }
                """;
    }

    private String successfulGroupResponse() {
        return """
                {
                  "ret":["SUCCESS::调用成功"],
                  "data":{
                    "itemGroupList":[
                      {"groupName":"在售","groupId":"58877261","defaultGroup":true}
                    ]
                  }
                }
                """;
    }

    private ItemListRespDTO sellerWorkbenchListResponse() {
        ItemDTO item = new ItemDTO();
        item.setId("item-1");
        item.setTitle("测试商品");
        item.setItemStatus(0);

        ItemListRespDTO respDTO = new ItemListRespDTO();
        respDTO.setSuccess(true);
        respDTO.setPageNumber(1);
        respDTO.setPageSize(20);
        respDTO.setCurrentCount(1);
        respDTO.setItems(List.of(item));
        return respDTO;
    }
}
