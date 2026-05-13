package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.service.ExcelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Excel导入导出控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/excel")
@CrossOrigin(origins = "*")
public class ExcelController {

    @Autowired
    private ExcelService excelService;

    /**
     * 导出订单数据
     */
    @GetMapping("/export/orders")
    public ResponseEntity<byte[]> exportOrders(@RequestParam(required = false) Long xianyuAccountId) {
        try {
            log.info("导出订单数据: accountId={}", xianyuAccountId);

            byte[] excelData = excelService.exportOrders(xianyuAccountId);

            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "订单数据_" + timestamp + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", encodedFilename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            log.info("订单数据导出成功: filename={}, size={}KB", filename, excelData.length / 1024);

            return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);

        } catch (Exception e) {
            log.error("导出订单数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导入卡密
     */
    @PostMapping("/import/kami")
    public ResultObject<ExcelService.ImportResult> importKami(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kamiConfigId") Long kamiConfigId) {
        try {
            log.info("导入卡密: kamiConfigId={}, filename={}", kamiConfigId, file.getOriginalFilename());

            if (file.isEmpty()) {
                return ResultObject.failed("文件为空");
            }

            if (kamiConfigId == null) {
                return ResultObject.failed("卡密配置ID不能为空");
            }

            // 检查文件类型
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                return ResultObject.failed("文件格式错误，仅支持.xlsx和.xls格式");
            }

            ExcelService.ImportResult result = excelService.importKami(file, kamiConfigId);

            if (result.isSuccess()) {
                return ResultObject.success(result);
            } else {
                return ResultObject.failed(result.getMessage());
            }

        } catch (Exception e) {
            log.error("导入卡密失败", e);
            return ResultObject.failed("导入失败: " + e.getMessage());
        }
    }

    /**
     * 下载卡密导入模板
     */
    @GetMapping("/template/kami")
    public ResponseEntity<byte[]> downloadKamiTemplate() {
        try {
            log.info("下载卡密导入模板");

            byte[] templateData = excelService.exportKamiTemplate();

            String filename = "卡密导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", encodedFilename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            log.info("卡密模板下载成功");

            return ResponseEntity.ok()
                .headers(headers)
                .body(templateData);

        } catch (Exception e) {
            log.error("下载卡密模板失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
