package com.feijimiao.xianyuassistant.service;

public interface BargainFreeShippingService {

    FreeShippingResult freeShipping(FreeShippingRequest request);

    record FreeShippingRequest(Long accountId, String orderId, String itemId, String buyerId) {
    }

    record FreeShippingResult(boolean success, String message, String responseBody) {
    }
}
