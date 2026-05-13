package com.feijimiao.xianyuassistant.service;

import lombok.Value;

@Value
class SliderAutoVerifyResult {
    Status status;
    SliderCaptchaBlock block;
    String message;

    static SliderAutoVerifyResult success() {
        return new SliderAutoVerifyResult(Status.SUCCESS, null, "自动滑块验证成功");
    }

    static SliderAutoVerifyResult failed(String message) {
        return new SliderAutoVerifyResult(Status.FAILED, null, message);
    }

    static SliderAutoVerifyResult hardBlock(SliderCaptchaBlock block) {
        String message = block == null ? "命中高风险验证码页" : block.getMessage();
        return new SliderAutoVerifyResult(Status.HARD_BLOCK, block, message);
    }

    boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    boolean isHardBlock() {
        return status == Status.HARD_BLOCK;
    }

    enum Status {
        SUCCESS,
        FAILED,
        HARD_BLOCK
    }
}
