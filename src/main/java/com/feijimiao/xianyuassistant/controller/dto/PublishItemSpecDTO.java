package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class PublishItemSpecDTO {
    private String propertyName;
    private Boolean supportImage;
    private List<PublishItemSpecValueDTO> propertyValues;
}
