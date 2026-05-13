package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiItemMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel导入导出服务
 *
 * 功能：
 * - 导出订单数据（包含收货人信息）
 * - 批量导入卡密
 */
@Slf4j
@Service
public class ExcelService {

    @Autowired
    private XianyuOrderMapper orderMapper;

    @Autowired
    private XianyuKamiItemMapper kamiItemMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出订单数据到Excel
     *
     * @param accountId 账号ID（可选）
     * @return Excel文件字节数组
     */
    public byte[] exportOrders(Long accountId) throws Exception {
        log.info("开始导出订单数据: accountId={}", accountId);

        // 查询订单
        LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) {
            wrapper.eq(XianyuOrder::getXianyuAccountId, accountId);
        }
        wrapper.orderByDesc(XianyuOrder::getOrderCreateTime);

        List<XianyuOrder> orders = orderMapper.selectList(wrapper);

        // 创建工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("订单数据");

        // 创建标题行样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "订单ID", "商品ID", "商品标题", "买家用户名", "订单状态",
            "订单金额", "收货人姓名", "收货人电话", "收货地址", "收货城市",
            "创建时间", "支付时间", "发货时间", "完成时间"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4000); // 设置列宽
        }

        // 填充数据
        int rowNum = 1;
        for (XianyuOrder order : orders) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(order.getOrderId() != null ? order.getOrderId() : "");
            row.createCell(1).setCellValue(order.getXyGoodsId() != null ? order.getXyGoodsId() : "");
            row.createCell(2).setCellValue(order.getGoodsTitle() != null ? order.getGoodsTitle() : "");
            row.createCell(3).setCellValue(order.getBuyerUserName() != null ? order.getBuyerUserName() : "");
            row.createCell(4).setCellValue(order.getOrderStatusText() != null ? order.getOrderStatusText() : "");
            row.createCell(5).setCellValue(order.getOrderAmountText() != null ? order.getOrderAmountText() : "");
            row.createCell(6).setCellValue(order.getReceiverName() != null ? order.getReceiverName() : "");
            row.createCell(7).setCellValue(order.getReceiverPhone() != null ? order.getReceiverPhone() : "");
            row.createCell(8).setCellValue(order.getReceiverAddress() != null ? order.getReceiverAddress() : "");
            row.createCell(9).setCellValue(order.getReceiverCity() != null ? order.getReceiverCity() : "");
            row.createCell(10).setCellValue(formatTimestamp(order.getOrderCreateTime()));
            row.createCell(11).setCellValue(formatTimestamp(order.getOrderPayTime()));
            row.createCell(12).setCellValue(formatTimestamp(order.getOrderDeliveryTime()));
            row.createCell(13).setCellValue(formatTimestamp(order.getOrderCompleteTime()));
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            // 设置最大宽度
            if (sheet.getColumnWidth(i) > 10000) {
                sheet.setColumnWidth(i, 10000);
            }
        }

        // 写入字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        log.info("订单数据导出完成: 订单数量={}", orders.size());
        return outputStream.toByteArray();
    }

    /**
     * 批量导入卡密
     *
     * @param file Excel文件
     * @param kamiConfigId 卡密配置ID
     * @return 导入结果
     */
    public ImportResult importKami(MultipartFile file, Long kamiConfigId) throws Exception {
        log.info("开始导入卡密: kamiConfigId={}", kamiConfigId);

        ImportResult result = new ImportResult();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows();

            if (totalRows <= 1) {
                result.setSuccess(false);
                result.setMessage("Excel文件为空或只有标题行");
                return result;
            }

            List<XianyuKamiItem> kamiItems = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            // 从第二行开始读取（跳过标题行）
            for (int i = 1; i < totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                try {
                    // 读取卡密内容（第一列）
                    Cell contentCell = row.getCell(0);
                    if (contentCell == null || contentCell.getCellType() == CellType.BLANK) {
                        errors.add("第" + (i + 1) + "行：卡密内容为空");
                        continue;
                    }

                    String content = getCellValueAsString(contentCell);
                    if (content == null || content.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行：卡密内容为空");
                        continue;
                    }

                    // 创建卡密对象
                    XianyuKamiItem kamiItem = new XianyuKamiItem();
                    kamiItem.setKamiConfigId(kamiConfigId);
                    kamiItem.setKamiContent(content.trim());
                    kamiItem.setStatus(0);
                    kamiItem.setSortOrder(i);

                    kamiItems.add(kamiItem);

                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行：解析失败 - " + e.getMessage());
                    log.error("解析第{}行失败", i + 1, e);
                }
            }

            // 批量插入数据库
            if (!kamiItems.isEmpty()) {
                for (XianyuKamiItem item : kamiItems) {
                    try {
                        kamiItemMapper.insert(item);
                        result.incrementSuccess();
                    } catch (Exception e) {
                        result.incrementFailed();
                        errors.add("插入卡密失败：" + item.getKamiContent() + " - " + e.getMessage());
                        log.error("插入卡密失败", e);
                    }
                }
            }

            result.setSuccess(true);
            result.setTotal(totalRows - 1);
            result.setErrors(errors);
            result.setMessage(String.format("导入完成：成功%d条，失败%d条", result.getSuccessCount(), result.getFailedCount()));

            log.info("卡密导入完成: 总数={}, 成功={}, 失败={}",
                result.getTotal(), result.getSuccessCount(), result.getFailedCount());

        } catch (Exception e) {
            log.error("导入卡密失败", e);
            result.setSuccess(false);
            result.setMessage("导入失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 导出卡密模板
     */
    public byte[] exportKamiTemplate() throws Exception {
        log.info("导出卡密模板");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("卡密数据");

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("卡密内容（必填）");
        headerRow.createCell(1).setCellValue("备注（可选）");

        // 添加示例数据
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue("ABCD-1234-EFGH-5678");
        exampleRow.createCell(1).setCellValue("示例卡密");

        // 设置列宽
        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 6000);

        // 写入字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null || timestamp == 0) {
            return "";
        }
        LocalDateTime dateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * 获取单元格值（字符串）
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                } else {
                    // 数字转字符串，去掉小数点
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * 导入结果
     */
    public static class ImportResult {
        private boolean success;
        private String message;
        private int total;
        private int successCount;
        private int failedCount;
        private List<String> errors = new ArrayList<>();

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public void incrementSuccess() {
            this.successCount++;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public void incrementFailed() {
            this.failedCount++;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }
}
