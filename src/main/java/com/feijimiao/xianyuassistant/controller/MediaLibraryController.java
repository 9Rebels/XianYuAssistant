package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuMediaLibrary;
import com.feijimiao.xianyuassistant.mapper.XianyuMediaLibraryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 媒体库控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*")
public class MediaLibraryController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 24;

    @Autowired
    private XianyuMediaLibraryMapper mediaLibraryMapper;

    @PostMapping("/list")
    public ResultObject<Map<String, Object>> list(@RequestBody Map<String, Object> req) {
        try {
            Long accountId = toLong(req.get("xianyuAccountId"));
            if (accountId == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            int pageNum = Math.max(DEFAULT_PAGE_NUM, toInt(req.get("pageNum"), DEFAULT_PAGE_NUM));
            int pageSize = Math.max(1, toInt(req.get("pageSize"), DEFAULT_PAGE_SIZE));
            String keyword = req.get("keyword") != null ? req.get("keyword").toString().trim() : null;
            int offset = (pageNum - 1) * pageSize;

            List<XianyuMediaLibrary> list = mediaLibraryMapper.selectByAccountId(accountId, keyword, pageSize, offset);
            long total = mediaLibraryMapper.countByAccountId(accountId, keyword);

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("查询媒体库失败", e);
            return ResultObject.failed("查询媒体库失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResultObject<Void> delete(@RequestBody Map<String, Object> req) {
        try {
            Long id = toLong(req.get("id"));
            if (id == null) {
                return ResultObject.failed("素材ID不能为空");
            }
            mediaLibraryMapper.deleteById(id);
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("删除媒体库素材失败", e);
            return ResultObject.failed("删除素材失败: " + e.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
