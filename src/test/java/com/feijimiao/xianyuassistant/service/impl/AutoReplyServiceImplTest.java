package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoReplyServiceImplTest {

    @Test
    void globalDisabledBlocksAllGoodsAutoReply() {
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        AutoReplyServiceImpl service = serviceWithGlobalSetting("0", goodsConfigMapper);

        assertFalse(service.isAutoReplyEnabled(1L, "goods-1"));
        verify(goodsConfigMapper, never()).selectByAccountAndGoodsId(1L, "goods-1");
    }

    @Test
    void globalEnabledAllowsAllGoodsAutoReply() {
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        AutoReplyServiceImpl service = serviceWithGlobalSetting("1", goodsConfigMapper);

        assertTrue(service.isAutoReplyEnabled(1L, "goods-1"));
        verify(goodsConfigMapper, never()).selectByAccountAndGoodsId(1L, "goods-1");
    }

    @Test
    void missingGlobalSettingFallsBackToGoodsSwitch() {
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        SysSettingService sysSettingService = mock(SysSettingService.class);
        AutoReplyServiceImpl service = new AutoReplyServiceImpl();
        XianyuGoodsConfig goodsConfig = new XianyuGoodsConfig();
        goodsConfig.setXianyuAutoReplyOn(1);

        ReflectionTestUtils.setField(service, "goodsConfigMapper", goodsConfigMapper);
        ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
        when(sysSettingService.getSettingValue("global_ai_reply_enabled_1")).thenReturn(null);
        when(goodsConfigMapper.selectByAccountAndGoodsId(1L, "goods-1")).thenReturn(goodsConfig);

        assertTrue(service.isAutoReplyEnabled(1L, "goods-1"));
        verify(goodsConfigMapper).selectByAccountAndGoodsId(1L, "goods-1");
    }

    private AutoReplyServiceImpl serviceWithGlobalSetting(String value, XianyuGoodsConfigMapper mapper) {
        AutoReplyServiceImpl service = new AutoReplyServiceImpl();
        SysSettingService sysSettingService = mock(SysSettingService.class);

        ReflectionTestUtils.setField(service, "goodsConfigMapper", mapper);
        ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
        when(sysSettingService.getSettingValue("global_ai_reply_enabled_1")).thenReturn(value);

        assertTrue("1".equals(value) || "0".equals(value));
        return service;
    }
}
