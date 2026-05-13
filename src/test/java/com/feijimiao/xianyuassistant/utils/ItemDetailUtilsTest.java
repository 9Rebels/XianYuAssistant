package com.feijimiao.xianyuassistant.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemDetailUtilsTest {

    @Test
    void extractsDescFromMtopResponse() {
        String detailJson = """
                {
                  "api": "mtop.taobao.idle.pc.detail",
                  "ret": ["SUCCESS::调用成功"],
                  "data": {
                    "itemDO": {
                      "desc": "完整响应里的商品详情"
                    }
                  }
                }
                """;

        assertEquals("完整响应里的商品详情", ItemDetailUtils.extractDescFromDetailJson(detailJson));
    }

    @Test
    void extractsDescFromDataPayload() {
        String detailJson = """
                {
                  "itemDO": {
                    "desc": "data对象里的商品详情"
                  }
                }
                """;

        assertEquals("data对象里的商品详情", ItemDetailUtils.extractDescFromDetailJson(detailJson));
    }

    @Test
    void keepsAlreadyExtractedText() {
        assertEquals("已经提取好的商品详情", ItemDetailUtils.extractDescFromDetailJson("已经提取好的商品详情"));
    }

    @Test
    void rejectsFailedMtopResponse() {
        String detailJson = """
                {
                  "api": "mtop.taobao.idle.pc.detail",
                  "ret": ["FAIL_SYS_TOKEN_EXOIRED::令牌过期"],
                  "data": {}
                }
                """;

        assertNull(ItemDetailUtils.extractDescFromDetailJson(detailJson));
    }
}
