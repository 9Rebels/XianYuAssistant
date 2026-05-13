package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ItemOperateServiceImplTest {

    @Test
    void normalAccountOffShelfUsesGoofishItemPageApi() {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();
        XianyuApiRecoveryService recoveryService = mock(XianyuApiRecoveryService.class);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", recoveryService);
        when(recoveryService.callApi(any(XianyuApiRecoveryRequest.class)))
                .thenReturn(apiSuccess());

        service.offShelfItem(normalAccount(), "cookie", goods());

        XianyuApiRecoveryRequest request = capturedRequest(recoveryService);
        assertEquals(1L, request.getAccountId());
        assertEquals("mtop.taobao.idle.item.downshelf", request.getApiName());
        assertEquals("2.0", request.getVersion());
        assertEquals("1048779334466", request.getDataMap().get("itemId"));
    }

    @Test
    void normalAccountDeleteUsesGoofishItemPageApi() {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();
        XianyuApiRecoveryService recoveryService = mock(XianyuApiRecoveryService.class);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", recoveryService);
        when(recoveryService.callApi(any(XianyuApiRecoveryRequest.class)))
                .thenReturn(apiSuccess());

        service.deleteItem(normalAccount(), "cookie", goods());

        XianyuApiRecoveryRequest request = capturedRequest(recoveryService);
        assertEquals("com.taobao.idle.item.delete", request.getApiName());
        assertEquals("1.1", request.getVersion());
        assertEquals("1048779334466", request.getDataMap().get("itemId"));
    }

    @Test
    void upShelfUsesEditDetailThenEditWithoutChangingItemId() {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();
        XianyuApiRecoveryService recoveryService = mock(XianyuApiRecoveryService.class);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", recoveryService);
        when(recoveryService.callApi(any(XianyuApiRecoveryRequest.class)))
                .thenReturn(apiSuccess("""
                        {"ret":["SUCCESS::调用成功"],"data":{"itemId":"1048779334466","title":"测试商品","price":"30.00"}}
                        """))
                .thenReturn(apiSuccess());

        service.upShelfItem(normalAccount(), "cookie", goods());

        List<XianyuApiRecoveryRequest> requests = capturedRequests(recoveryService, 2);
        XianyuApiRecoveryRequest editDetailRequest = requests.get(0);
        assertEquals("mtop.idle.pc.idleitem.editDetail", editDetailRequest.getApiName());
        assertEquals("1.0", editDetailRequest.getVersion());
        assertEquals("1048779334466", editDetailRequest.getDataMap().get("itemId"));

        XianyuApiRecoveryRequest editRequest = requests.get(1);
        Map<String, Object> editData = editRequest.getDataMap();
        assertEquals("mtop.idle.pc.idleitem.edit", editRequest.getApiName());
        assertEquals("1048779334466", editData.get("itemId"));
        assertEquals("测试商品", editData.get("title"));
        assertEquals("pcMainPublish", editData.get("sourceId"));
        assertEquals("pcMainPublish", editData.get("bizcode"));
        assertEquals("pcMainPublish", editData.get("publishScene"));
        assertTrue(editData.get("uniqueCode") != null && !String.valueOf(editData.get("uniqueCode")).isBlank());
    }

    @Test
    void normalAccountUpdatePriceFailsBeforeRemoteCall() {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();
        XianyuApiRecoveryService recoveryService = mock(XianyuApiRecoveryService.class);
        ReflectionTestUtils.setField(service, "xianyuApiRecoveryService", recoveryService);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.updatePrice(normalAccount(), "cookie", goods(), "30.00"));

        assertEquals("当前账号不是鱼小铺，无法改价", error.getMessage());
        verifyNoInteractions(recoveryService);
    }

    @Test
    void buildSellerPriceUpdatePayloadForSingleSkuItem() throws Exception {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();

        Map<String, Object> data = service.buildSellerPriceUpdatePayload(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"itemId":"1001","quantity":"7","idleItemSkuList":[]}
                        """),
                "12.50");

        assertEquals("1001", data.get("itemId"));
        assertEquals(7, data.get("quantity"));
        assertEquals("12.50", data.get("price"));
    }

    @Test
    void buildSellerPriceUpdatePayloadForSkuItem() throws Exception {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();

        Map<String, Object> data = service.buildSellerPriceUpdatePayload(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"itemId":"1001","idleItemSkuList":[{"skuId":"s1","quantity":2},{"skuId":"s2","quantity":"3"}]}
                        """),
                "12.50");

        assertEquals("1001", data.get("itemId"));
        String skuList = String.valueOf(data.get("itemSkuListStr"));
        assertTrue(skuList.contains("\"skuId\":\"s1\""));
        assertTrue(skuList.contains("\"quantity\":2"));
        assertTrue(skuList.contains("\"price\":\"12.50\""));
        assertTrue(skuList.contains("\"skuId\":\"s2\""));
    }

    @Test
    void buildSellerQuantityUpdatePayloadFallsBackToLocalPriceWhenSellerPriceMissing() throws Exception {
        ItemOperateServiceImpl service = new ItemOperateServiceImpl();

        Map<String, Object> data = service.buildSellerQuantityUpdatePayload(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"itemId":"1001","quantity":"7","idleItemSkuList":[]}
                        """),
                12,
                "30.00");

        assertEquals("1001", data.get("itemId"));
        assertEquals(12, data.get("quantity"));
        assertEquals("30.00", data.get("price"));
    }

    private XianyuApiRecoveryRequest capturedRequest(XianyuApiRecoveryService recoveryService) {
        ArgumentCaptor<XianyuApiRecoveryRequest> captor =
                ArgumentCaptor.forClass(XianyuApiRecoveryRequest.class);
        verify(recoveryService).callApi(captor.capture());
        return captor.getValue();
    }

    private List<XianyuApiRecoveryRequest> capturedRequests(
            XianyuApiRecoveryService recoveryService, int count) {
        ArgumentCaptor<XianyuApiRecoveryRequest> captor =
                ArgumentCaptor.forClass(XianyuApiRecoveryRequest.class);
        verify(recoveryService, times(count)).callApi(captor.capture());
        return captor.getAllValues();
    }

    private XianyuApiRecoveryResult apiSuccess() {
        return XianyuApiRecoveryResult.success("{\"ret\":[\"SUCCESS::调用成功\"]}", "cookie", false, null);
    }

    private XianyuApiRecoveryResult apiSuccess(String response) {
        return XianyuApiRecoveryResult.success(response, "cookie", false, null);
    }

    private XianyuAccount normalAccount() {
        XianyuAccount account = new XianyuAccount();
        account.setId(1L);
        account.setFishShopUser(false);
        return account;
    }

    private XianyuGoodsInfo goods() {
        XianyuGoodsInfo goods = new XianyuGoodsInfo();
        goods.setXyGoodId("1048779334466");
        goods.setXianyuAccountId(1L);
        goods.setTitle("测试商品");
        goods.setStatus(0);
        goods.setSoldPrice("30.00");
        return goods;
    }
}
