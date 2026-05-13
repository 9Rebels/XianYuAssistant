package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishResultDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishTaskReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishTaskRespDTO;
import com.feijimiao.xianyuassistant.service.ItemPolishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/item-polish")
@CrossOrigin(origins = "*")
public class ItemPolishController {

    @Autowired
    private ItemPolishService itemPolishService;

    @PostMapping("/run")
    public ResultObject<ItemPolishResultDTO> run(@RequestBody Map<String, Object> params) {
        try {
            Long accountId = Long.parseLong(String.valueOf(params.get("xianyuAccountId")));
            log.info("手动擦亮请求: accountId={}", accountId);
            return itemPolishService.polishAccountItems(accountId);
        } catch (Exception e) {
            log.error("手动擦亮失败", e);
            return ResultObject.failed("手动擦亮失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/get")
    public ResultObject<ItemPolishTaskRespDTO> getTask(@RequestBody Map<String, Object> params) {
        try {
            Long accountId = Long.parseLong(String.valueOf(params.get("xianyuAccountId")));
            return itemPolishService.getTask(accountId);
        } catch (Exception e) {
            log.error("获取定时擦亮设置失败", e);
            return ResultObject.failed("获取定时擦亮设置失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/save")
    public ResultObject<ItemPolishTaskRespDTO> saveTask(@RequestBody ItemPolishTaskReqDTO reqDTO) {
        try {
            return itemPolishService.saveTask(reqDTO);
        } catch (Exception e) {
            log.error("保存定时擦亮设置失败", e);
            return ResultObject.failed("保存定时擦亮设置失败: " + e.getMessage());
        }
    }
}
