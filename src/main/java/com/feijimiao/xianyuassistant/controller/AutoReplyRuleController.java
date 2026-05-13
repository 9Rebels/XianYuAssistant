package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleBatchImportReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleQueryReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleRespDTO;
import com.feijimiao.xianyuassistant.service.AutoReplyRuleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自动回复规则控制器
 */
@RestController
@RequestMapping("/api/auto-reply-rule")
@CrossOrigin(origins = "*")
public class AutoReplyRuleController {

    @Autowired
    private AutoReplyRuleService autoReplyRuleService;

    @PostMapping("/list")
    public ResultObject<List<AutoReplyRuleRespDTO>> list(@Valid @RequestBody AutoReplyRuleQueryReqDTO reqDTO) {
        return autoReplyRuleService.list(reqDTO);
    }

    @PostMapping("/save")
    public ResultObject<AutoReplyRuleRespDTO> save(@Valid @RequestBody AutoReplyRuleReqDTO reqDTO) {
        return autoReplyRuleService.save(reqDTO);
    }

    @PostMapping("/batchImport")
    public ResultObject<Integer> batchImport(@Valid @RequestBody AutoReplyRuleBatchImportReqDTO reqDTO) {
        return autoReplyRuleService.batchImport(reqDTO);
    }

    @PostMapping("/delete")
    public ResultObject<Void> delete(@RequestParam("id") Long id) {
        return autoReplyRuleService.delete(id);
    }
}
