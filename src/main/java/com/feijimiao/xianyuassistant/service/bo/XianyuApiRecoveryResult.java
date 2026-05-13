package com.feijimiao.xianyuassistant.service.bo;

import com.feijimiao.xianyuassistant.utils.XianyuApiUtils;
import lombok.Data;

@Data
public class XianyuApiRecoveryResult {
    private boolean success;
    private boolean recovered;
    private String response;
    private String cookieText;
    private String errorMessage;
    private CookieRecoveryResult recoveryResult;
    private XianyuApiUtils.ApiCallResultWithHeaders headersResult;

    public static XianyuApiRecoveryResult success(String response, String cookieText,
                                                  boolean recovered,
                                                  XianyuApiUtils.ApiCallResultWithHeaders headersResult) {
        XianyuApiRecoveryResult result = new XianyuApiRecoveryResult();
        result.setSuccess(true);
        result.setResponse(response);
        result.setCookieText(cookieText);
        result.setRecovered(recovered);
        result.setHeadersResult(headersResult);
        return result;
    }

    public static XianyuApiRecoveryResult failed(String response, String errorMessage,
                                                 CookieRecoveryResult recoveryResult) {
        XianyuApiRecoveryResult result = new XianyuApiRecoveryResult();
        result.setSuccess(false);
        result.setResponse(response);
        result.setErrorMessage(errorMessage);
        result.setRecoveryResult(recoveryResult);
        return result;
    }
}
