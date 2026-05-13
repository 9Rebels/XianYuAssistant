package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishResultDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishTaskReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemPolishTaskRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.XianyuItemPolishTask;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuItemPolishTaskMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.ItemPolishService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ItemPolishServiceImpl implements ItemPolishService {

    private static final int STATUS_ON_SALE = 0;
    private static final int DEFAULT_RUN_HOUR = 8;
    private static final int DEFAULT_RANDOM_DELAY_MINUTES = 10;
    private static final int MAX_DUE_TASKS = 5;
    private static final int POLISH_MIN_DELAY_MS = 1000;
    private static final int POLISH_MAX_DELAY_MS = 3000;
    private static final String POLISH_API = "mtop.taobao.idle.item.polish";
    private static final String POLISH_BACKUP_API = "mtop.idle.item.polish";
    private static final String SPM_CNT = "a21ybx.im.0.0";
    private static final String SPM_PRE = "a21ybx.collection.menu.1.272b5141NafCNK";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();
    private final Set<Long> runningAccounts = ConcurrentHashMap.newKeySet();

    @Autowired
    private AccountService accountService;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private XianyuItemPolishTaskMapper taskMapper;

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    @Override
    public ResultObject<ItemPolishResultDTO> polishAccountItems(Long accountId) {
        if (accountId == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        if (!runningAccounts.add(accountId)) {
            return ResultObject.failed("该账号正在擦亮中，请稍后再试");
        }
        try {
            return ResultObject.success(doPolishAccountItems(accountId));
        } catch (Exception e) {
            log.error("账号商品擦亮失败: accountId={}", accountId, e);
            return ResultObject.failed("擦亮失败: " + e.getMessage());
        } finally {
            runningAccounts.remove(accountId);
        }
    }

    @Override
    public ResultObject<ItemPolishTaskRespDTO> getTask(Long accountId) {
        if (accountId == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        ItemPolishTaskRespDTO respDTO = new ItemPolishTaskRespDTO();
        respDTO.setTask(taskMapper.selectByAccountId(accountId));
        return ResultObject.success(respDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultObject<ItemPolishTaskRespDTO> saveTask(ItemPolishTaskReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        int runHour = normalizeRunHour(reqDTO.getRunHour());
        int randomDelay = normalizeRandomDelay(reqDTO.getRandomDelayMaxMinutes());
        int enabled = reqDTO.getEnabled() != null && reqDTO.getEnabled() == 0 ? 0 : 1;

        XianyuItemPolishTask task = taskMapper.selectByAccountId(reqDTO.getXianyuAccountId());
        if (task == null) {
            task = new XianyuItemPolishTask();
            task.setXianyuAccountId(reqDTO.getXianyuAccountId());
            task.setCreateTime(DateTimeUtils.currentShanghaiTime());
        }
        task.setEnabled(enabled);
        task.setRunHour(runHour);
        task.setRandomDelayMaxMinutes(randomDelay);
        task.setNextRunTime(enabled == 1 ? calculateNextRunTime(runHour, randomDelay, true) : null);
        task.setUpdateTime(DateTimeUtils.currentShanghaiTime());

        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }

        ItemPolishTaskRespDTO respDTO = new ItemPolishTaskRespDTO();
        respDTO.setTask(task);
        return ResultObject.success(respDTO, "定时擦亮设置已保存");
    }

    @Override
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void executeDueTasks() {
        List<XianyuItemPolishTask> dueTasks = taskMapper.selectDueTasks(MAX_DUE_TASKS);
        for (XianyuItemPolishTask task : dueTasks) {
            executeDueTask(task);
        }
    }

    private void executeDueTask(XianyuItemPolishTask task) {
        Long accountId = task.getXianyuAccountId();
        if (!runningAccounts.add(accountId)) {
            log.info("账号擦亮任务正在执行，跳过本轮: accountId={}", accountId);
            return;
        }
        try {
            ItemPolishResultDTO result = doPolishAccountItems(accountId);
            updateTaskAfterRun(task, result);
        } catch (Exception e) {
            log.error("执行定时擦亮任务失败: accountId={}", accountId, e);
            ItemPolishResultDTO failedResult = new ItemPolishResultDTO();
            failedResult.setSuccess(false);
            failedResult.setXianyuAccountId(accountId);
            failedResult.setMessage(e.getMessage());
            updateTaskAfterRun(task, failedResult);
        } finally {
            runningAccounts.remove(accountId);
        }
    }

    private ItemPolishResultDTO doPolishAccountItems(Long accountId) throws Exception {
        String cookie = accountService.getCookieByAccountId(accountId);
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalStateException("未找到账号Cookie，请先登录");
        }

        List<XianyuGoodsInfo> goodsList = listOnSaleGoods(accountId);
        ItemPolishResultDTO result = new ItemPolishResultDTO();
        result.setSuccess(true);
        result.setXianyuAccountId(accountId);
        result.setTotal(goodsList.size());
        result.setPolished(0);
        result.setFailed(0);

        for (int index = 0; index < goodsList.size(); index++) {
            XianyuGoodsInfo goods = goodsList.get(index);
            ItemPolishResultDTO.ItemPolishResultItemDTO itemResult = polishGoods(accountId, cookie, goods);
            result.getResults().add(itemResult);
            if (Boolean.TRUE.equals(itemResult.getSuccess())) {
                result.setPolished(result.getPolished() + 1);
            } else {
                result.setFailed(result.getFailed() + 1);
            }
            if (Boolean.TRUE.equals(itemResult.getNeedManual())) {
                applyRecoveryInfo(result, itemResult);
                result.setSuccess(false);
                break;
            }
            if (index < goodsList.size() - 1) {
                HumanLikeDelayUtils.delay(POLISH_MIN_DELAY_MS, POLISH_MAX_DELAY_MS);
            }
        }

        if (Boolean.TRUE.equals(result.getNeedManual())) {
            result.setMessage("擦亮暂停：需要完成滑块验证后重试");
        } else {
            result.setMessage("擦亮完成：成功 " + result.getPolished() + "，失败 " + result.getFailed());
        }
        return result;
    }

    private List<XianyuGoodsInfo> listOnSaleGoods(Long accountId) {
        LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, accountId);
        queryWrapper.eq(XianyuGoodsInfo::getStatus, STATUS_ON_SALE);
        queryWrapper.orderByAsc(XianyuGoodsInfo::getSortOrder).orderByAsc(XianyuGoodsInfo::getId);
        List<XianyuGoodsInfo> goodsList = goodsInfoMapper.selectList(queryWrapper);
        return goodsList != null ? goodsList : Collections.emptyList();
    }

    private ItemPolishResultDTO.ItemPolishResultItemDTO polishGoods(Long accountId, String cookie,
                                                                    XianyuGoodsInfo goods) {
        ItemPolishResultDTO.ItemPolishResultItemDTO itemResult = new ItemPolishResultDTO.ItemPolishResultItemDTO();
        itemResult.setXyGoodId(goods.getXyGoodId());
        itemResult.setTitle(goods.getTitle());

        String error = callPolishApi(accountId, cookie, POLISH_API, goods.getXyGoodId(), itemResult);
        if (error == null) {
            itemResult.setSuccess(true);
            return itemResult;
        }
        if (Boolean.TRUE.equals(itemResult.getNeedManual())) {
            itemResult.setSuccess(false);
            itemResult.setError(error);
            return itemResult;
        }

        String backupError = callPolishApi(accountId, cookie, POLISH_BACKUP_API, goods.getXyGoodId(), itemResult);
        if (backupError == null) {
            itemResult.setSuccess(true);
            return itemResult;
        }

        itemResult.setSuccess(false);
        itemResult.setError(backupError.isBlank() ? error : backupError);
        return itemResult;
    }

    @SuppressWarnings("unchecked")
    private String callPolishApi(Long accountId, String cookie, String apiName, String xyGoodId,
                                 ItemPolishResultDTO.ItemPolishResultItemDTO itemResult) {
        try {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("itemId", xyGoodId);
            XianyuApiRecoveryResult apiResult = xianyuApiRecoveryService.callApi(
                    buildApiRequest(accountId, "商品擦亮", apiName, dataMap, cookie));
            if (!apiResult.isSuccess()) {
                applyRecoveryInfo(itemResult, apiResult);
                return buildRecoveryMessage(apiResult, "擦亮失败");
            }

            Map<String, Object> responseMap = objectMapper.readValue(apiResult.getResponse(), Map.class);
            List<Object> ret = (List<Object>) responseMap.get("ret");
            String retMsg = ret != null && !ret.isEmpty() ? String.valueOf(ret.get(0)) : "";
            if (retMsg.contains("SUCCESS") || retMsg.contains("调用成功")) {
                return null;
            }
            return retMsg.isBlank() ? "擦亮失败" : retMsg;
        } catch (Exception e) {
            log.warn("调用擦亮接口异常: apiName={}, xyGoodId={}, error={}", apiName, xyGoodId, e.getMessage());
            return e.getMessage();
        }
    }

    private void applyRecoveryInfo(ItemPolishResultDTO result,
                                   ItemPolishResultDTO.ItemPolishResultItemDTO itemResult) {
        result.setRecoveryAttempted(itemResult.getRecoveryAttempted());
        result.setNeedCaptcha(itemResult.getNeedCaptcha());
        result.setNeedManual(itemResult.getNeedManual());
        result.setManualVerifyUrl(itemResult.getManualVerifyUrl());
        result.setCaptchaUrl(itemResult.getCaptchaUrl());
        result.setSessionId(itemResult.getSessionId());
    }

    private void applyRecoveryInfo(ItemPolishResultDTO.ItemPolishResultItemDTO itemResult,
                                   XianyuApiRecoveryResult apiResult) {
        if (apiResult == null || apiResult.getRecoveryResult() == null) {
            return;
        }
        itemResult.setRecoveryAttempted(apiResult.getRecoveryResult().isAttempted());
        itemResult.setNeedCaptcha(apiResult.getRecoveryResult().isNeedCaptcha());
        itemResult.setNeedManual(apiResult.getRecoveryResult().isNeedManual());
        itemResult.setManualVerifyUrl(apiResult.getRecoveryResult().getManualVerifyUrl());
        itemResult.setCaptchaUrl(apiResult.getRecoveryResult().getCaptchaUrl());
        itemResult.setSessionId(apiResult.getRecoveryResult().getSessionId());
    }

    private XianyuApiRecoveryRequest buildApiRequest(Long accountId, String operationName, String apiName,
                                                     Map<String, Object> dataMap, String cookie) {
        XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
        request.setAccountId(accountId);
        request.setOperationName(operationName);
        request.setApiName(apiName);
        request.setDataMap(dataMap);
        request.setCookie(cookie);
        request.setSpmCnt(SPM_CNT);
        request.setSpmPre(SPM_PRE);
        return request;
    }

    private String buildRecoveryMessage(XianyuApiRecoveryResult apiResult, String fallback) {
        if (apiResult == null) {
            return fallback;
        }
        if (apiResult.getRecoveryResult() != null && apiResult.getRecoveryResult().isNeedManual()) {
            return apiResult.getErrorMessage() + "，请完成滑块验证后重试";
        }
        return apiResult.getErrorMessage() == null || apiResult.getErrorMessage().isBlank()
                ? fallback
                : apiResult.getErrorMessage();
    }

    private void updateTaskAfterRun(XianyuItemPolishTask task, ItemPolishResultDTO result) {
        try {
            task.setLastRunTime(DateTimeUtils.currentShanghaiTime());
            task.setLastResult(objectMapper.writeValueAsString(result));
            task.setNextRunTime(calculateNextRunTime(task.getRunHour(), task.getRandomDelayMaxMinutes(), false));
            task.setUpdateTime(DateTimeUtils.currentShanghaiTime());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.error("更新擦亮任务结果失败: taskId={}", task.getId(), e);
        }
    }

    private int normalizeRunHour(Integer runHour) {
        if (runHour == null) {
            return DEFAULT_RUN_HOUR;
        }
        if (runHour < 0 || runHour > 23) {
            throw new IllegalArgumentException("执行时段必须在0-23之间");
        }
        return runHour;
    }

    private int normalizeRandomDelay(Integer randomDelay) {
        if (randomDelay == null) {
            return DEFAULT_RANDOM_DELAY_MINUTES;
        }
        if (randomDelay < 0 || randomDelay > 180) {
            throw new IllegalArgumentException("随机延迟必须在0-180分钟之间");
        }
        return randomDelay;
    }

    private String calculateNextRunTime(Integer runHour, Integer randomDelayMaxMinutes, boolean includeToday) {
        int normalizedHour = normalizeRunHour(runHour);
        int normalizedDelay = normalizeRandomDelay(randomDelayMaxMinutes);
        int delayMinutes = normalizedDelay > 0 ? random.nextInt(normalizedDelay + 1) : 0;
        LocalDate today = LocalDate.now();
        LocalDateTime nextRun = today.atTime(normalizedHour, 0).plusMinutes(delayMinutes);
        if (!includeToday || !nextRun.isAfter(LocalDateTime.now())) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun.format(DATE_TIME_FORMATTER);
    }
}
