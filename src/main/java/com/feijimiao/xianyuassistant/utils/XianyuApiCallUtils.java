package com.feijimiao.xianyuassistant.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 闲鱼API调用工具类（带自动刷新机制）
 * 参考Python Demo的被动刷新策略
 */
@Slf4j
@Component
public class XianyuApiCallUtils {

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    /**
     * 调用闲鱼API（带自动刷新机制）
     * 
     * @param accountId 账号ID
     * @param apiName API名称
     * @param dataMap 数据Map
     * @param cookiesStr Cookie字符串
     * @return API响应结果
     */
    public ApiCallResult callApiWithRetry(Long accountId, String apiName, 
                                          Map<String, Object> dataMap, String cookiesStr) {
        try {
            XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
            request.setAccountId(accountId);
            request.setOperationName("闲鱼API调用");
            request.setApiName(apiName);
            request.setDataMap(dataMap);
            request.setCookie(cookiesStr);
            XianyuApiRecoveryResult result = xianyuApiRecoveryService.callApi(request);
            return new ApiCallResult(result.isSuccess(), result.getResponse(),
                    result.getErrorMessage(), isTokenExpired(result.getErrorMessage()));
        } catch (Exception e) {
            log.error("【账号{}】API调用异常: apiName={}", accountId, apiName, e);
            return new ApiCallResult(false, null, "调用异常: " + e.getMessage(), false);
        }
    }
    
    /**
     * 判断是否为令牌过期错误
     */
    private boolean isTokenExpired(String retCode) {
        if (retCode == null) {
            return false;
        }
        return retCode.contains("FAIL_SYS_TOKEN_EXOIRED") ||  // 注意：API返回的拼写错误
               retCode.contains("FAIL_SYS_TOKEN_EXPIRED") ||
               retCode.contains("FAIL_SYS_SESSION_EXPIRED") ||
               retCode.contains("令牌过期");
    }
    
    /**
     * API调用结果封装类
     */
    public static class ApiCallResult {
        private final boolean success;
        private final String response;
        private final String errorMessage;
        private final boolean tokenExpired;
        
        public ApiCallResult(boolean success, String response, String errorMessage, boolean tokenExpired) {
            this.success = success;
            this.response = response;
            this.errorMessage = errorMessage;
            this.tokenExpired = tokenExpired;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getResponse() {
            return response;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public boolean isTokenExpired() {
            return tokenExpired;
        }
        
        /**
         * 从响应中提取data字段
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> extractData() {
            if (response == null || response.isEmpty()) {
                return null;
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> responseMap = mapper.readValue(response, Map.class);
                return (Map<String, Object>) responseMap.get("data");
            } catch (Exception e) {
                log.error("提取data字段失败", e);
                return null;
            }
        }
    }
}
