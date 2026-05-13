package com.feijimiao.xianyuassistant.service;

import lombok.Value;

@Value
class SliderCaptchaBlock {
    String kind;
    String url;
    String title;
    String message;
}
