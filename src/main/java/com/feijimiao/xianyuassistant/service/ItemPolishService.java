package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishResultDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishTaskReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishTaskRespDTO;

public interface ItemPolishService {

    ResultObject<ItemPolishResultDTO> polishAccountItems(Long accountId);

    ResultObject<ItemPolishTaskRespDTO> getTask(Long accountId);

    ResultObject<ItemPolishTaskRespDTO> saveTask(ItemPolishTaskReqDTO reqDTO);

    void executeDueTasks();
}
