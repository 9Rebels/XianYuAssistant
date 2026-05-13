package com.feijimiao.xianyuassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.PoiCacheQueryReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.PoiCacheSaveReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.PoiCandidateDTO;
import com.feijimiao.xianyuassistant.controller.dto.PoiFetchReqDTO;
import com.feijimiao.xianyuassistant.entity.XianyuPoiCache;
import com.feijimiao.xianyuassistant.mapper.XianyuPoiCacheMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/poi")
@CrossOrigin(origins = "*")
public class PoiCacheController {

    private static final String LOCAL_POI_API = "mtop.taobao.idle.local.poi.get";
    private static final String DEFAULT_LONGITUDE = "116.397128";
    private static final String DEFAULT_LATITUDE = "39.916527";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private XianyuPoiCacheMapper poiCacheMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    @PostMapping("/cache")
    public ResultObject<XianyuPoiCache> cache(@RequestBody PoiCacheQueryReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null || reqDTO.getDivisionId() == null) {
            return ResultObject.failed("账号ID和地区编码不能为空");
        }
        XianyuPoiCache cache = poiCacheMapper.selectByAccountAndDivision(
                reqDTO.getXianyuAccountId(), reqDTO.getDivisionId());
        return ResultObject.success(cache);
    }

    @PostMapping("/default")
    public ResultObject<XianyuPoiCache> defaultPoi(@RequestBody PoiCacheQueryReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        return ResultObject.success(poiCacheMapper.selectDefaultByAccountId(reqDTO.getXianyuAccountId()));
    }

    @PostMapping("/list")
    public ResultObject<List<XianyuPoiCache>> list(@RequestBody PoiCacheQueryReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        String keyword = reqDTO.getKeyword() != null ? reqDTO.getKeyword().trim() : null;
        return ResultObject.success(poiCacheMapper.selectByAccountId(reqDTO.getXianyuAccountId(), keyword));
    }

    @PostMapping("/fetch")
    public ResultObject<List<PoiCandidateDTO>> fetch(@RequestBody PoiFetchReqDTO reqDTO) {
        try {
            String error = validateFetch(reqDTO);
            if (error != null) {
                return ResultObject.failed(error);
            }
            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("longitude", valueOrDefault(reqDTO.getLongitude(), DEFAULT_LONGITUDE));
            data.put("latitude", valueOrDefault(reqDTO.getLatitude(), DEFAULT_LATITUDE));
            XianyuApiRecoveryResult apiResult = xianyuApiRecoveryService.callApi(
                    buildApiRequest(reqDTO.getXianyuAccountId(), "地图点位获取",
                            LOCAL_POI_API, data, cookie, "a21ybx.publish.0.0", null));
            if (!apiResult.isSuccess()) {
                return ResultObject.failed(buildRecoveryMessage(apiResult, "获取地图点位失败，请稍后重试"));
            }

            List<PoiCandidateDTO> candidates = parseCandidates(apiResult.getResponse(), reqDTO);
            if (candidates.isEmpty()) {
                log.warn("闲鱼地图点位未返回可用POI: accountId={}, divisionId={}, response={}",
                        reqDTO.getXianyuAccountId(), reqDTO.getDivisionId(),
                        truncate(apiResult.getResponse(), 1200));
                return ResultObject.failed("未获取到可用地图点位，请稍后重试或换个地区");
            }
            return ResultObject.success(candidates);
        } catch (Exception e) {
            log.error("获取地图点位异常: accountId={}", reqDTO != null ? reqDTO.getXianyuAccountId() : null, e);
            return ResultObject.failed("获取地图点位异常: " + e.getMessage());
        }
    }

    private XianyuApiRecoveryRequest buildApiRequest(Long accountId, String operationName, String apiName,
                                                     Map<String, Object> data, String cookie,
                                                     String spmCnt, String spmPre) {
        XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
        request.setAccountId(accountId);
        request.setOperationName(operationName);
        request.setApiName(apiName);
        request.setDataMap(data);
        request.setCookie(cookie);
        request.setSpmCnt(spmCnt);
        request.setSpmPre(spmPre);
        return request;
    }

    private String buildRecoveryMessage(XianyuApiRecoveryResult apiResult, String fallback) {
        if (apiResult != null && apiResult.getRecoveryResult() != null
                && apiResult.getRecoveryResult().isNeedManual()) {
            return apiResult.getErrorMessage() + "，请到连接管理完成滑块验证后重试";
        }
        if (apiResult != null && apiResult.getErrorMessage() != null
                && !apiResult.getErrorMessage().isBlank()) {
            return apiResult.getErrorMessage();
        }
        return fallback;
    }

    @PostMapping("/save")
    public ResultObject<XianyuPoiCache> save(@RequestBody PoiCacheSaveReqDTO reqDTO) {
        String error = validateSave(reqDTO);
        if (error != null) {
            return ResultObject.failed(error);
        }
        if (Boolean.TRUE.equals(reqDTO.getDefaultPoi())) {
            poiCacheMapper.clearDefault(reqDTO.getXianyuAccountId());
        }
        XianyuPoiCache cache = toCache(reqDTO);
        poiCacheMapper.upsert(cache);
        XianyuPoiCache saved = poiCacheMapper.selectByAccountAndDivision(
                reqDTO.getXianyuAccountId(), reqDTO.getDivisionId());
        return ResultObject.success(saved);
    }

    private String validateFetch(PoiFetchReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
            return "账号ID不能为空";
        }
        if (reqDTO.getDivisionId() == null || reqDTO.getDivisionId() <= 0) {
            return "地区编码不能为空";
        }
        return null;
    }

    private String validateSave(PoiCacheSaveReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
            return "账号ID不能为空";
        }
        if (reqDTO.getDivisionId() == null || reqDTO.getDivisionId() <= 0) {
            return "地区编码不能为空";
        }
        if (isBlank(reqDTO.getProv()) || isBlank(reqDTO.getCity())) {
            return "省份和城市不能为空";
        }
        if (isBlank(reqDTO.getPoiId()) || isBlank(reqDTO.getPoiName()) || isBlank(reqDTO.getGps())) {
            return "地图点位不完整";
        }
        return null;
    }

    private List<PoiCandidateDTO> parseCandidates(String response, PoiFetchReqDTO reqDTO) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        List<String> ret = (List<String>) responseMap.get("ret");
        if (ret == null || ret.isEmpty() || !ret.get(0).contains("SUCCESS")) {
            log.warn("闲鱼地图点位接口失败: ret={}, response={}", ret, truncate(response, 1200));
            return List.of();
        }

        Map<String, Object> data = asMap(responseMap.get("data"));
        List<PoiCandidateDTO> result = new ArrayList<>();
        addCandidate(result, findMapValue(data, "selectedPoi"), reqDTO);
        addCandidates(result, findListValue(data, "commonAddresses"), reqDTO);
        addCandidates(result, findListValue(data, "nearbyPoiList"), reqDTO);
        addCandidates(result, findListValue(data, "pois"), reqDTO);
        return result;
    }

    private void addCandidates(List<PoiCandidateDTO> result, List<Object> values, PoiFetchReqDTO reqDTO) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            addCandidate(result, asMap(value), reqDTO);
        }
    }

    private void addCandidate(List<PoiCandidateDTO> result, Map<String, Object> poi, PoiFetchReqDTO reqDTO) {
        if (poi == null) {
            return;
        }
        PoiCandidateDTO candidate = toCandidate(poi, reqDTO);
        if (candidate == null || containsPoi(result, candidate.getPoiId())) {
            return;
        }
        result.add(candidate);
    }

    private PoiCandidateDTO toCandidate(Map<String, Object> poi, PoiFetchReqDTO reqDTO) {
        String poiId = firstString(poi, "poiId", "id");
        String poiName = firstString(poi, "poi", "poiName", "name");
        String longitude = firstString(poi, "longitude", "lng");
        String latitude = firstString(poi, "latitude", "lat");
        if (isBlank(poiId) || isBlank(poiName) || isBlank(longitude) || isBlank(latitude)) {
            return null;
        }
        PoiCandidateDTO candidate = new PoiCandidateDTO();
        candidate.setProv(valueOrDefault(firstString(poi, "prov"), reqDTO.getProv()));
        candidate.setCity(valueOrDefault(firstString(poi, "city"), reqDTO.getCity()));
        candidate.setArea(valueOrDefault(firstString(poi, "area"), reqDTO.getArea()));
        candidate.setDivisionId(toInteger(firstString(poi, "divisionId"), reqDTO.getDivisionId()));
        candidate.setPoiId(poiId);
        candidate.setPoiName(poiName);
        candidate.setLatitude(latitude);
        candidate.setLongitude(longitude);
        candidate.setGps(latitude + "," + longitude);
        candidate.setAddress(firstString(poi, "address", "aoi"));
        return candidate;
    }

    private XianyuPoiCache toCache(PoiCacheSaveReqDTO reqDTO) {
        XianyuPoiCache cache = new XianyuPoiCache();
        cache.setXianyuAccountId(reqDTO.getXianyuAccountId());
        cache.setDivisionId(reqDTO.getDivisionId());
        cache.setProv(reqDTO.getProv().trim());
        cache.setCity(reqDTO.getCity().trim());
        cache.setArea(valueOrEmpty(reqDTO.getArea()));
        cache.setPoiId(reqDTO.getPoiId().trim());
        cache.setPoiName(reqDTO.getPoiName().trim());
        cache.setGps(reqDTO.getGps().trim());
        cache.setLatitude(valueOrEmpty(reqDTO.getLatitude()));
        cache.setLongitude(valueOrEmpty(reqDTO.getLongitude()));
        cache.setAddress(valueOrEmpty(reqDTO.getAddress()));
        cache.setSource("official_poi");
        cache.setIsDefault(Boolean.TRUE.equals(reqDTO.getDefaultPoi()) ? 1 : 0);
        return cache;
    }

    private Map<String, Object> findMapValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> direct = asMap(map.get(key));
            if (direct != null) {
                return direct;
            }
            for (Object child : map.values()) {
                Map<String, Object> found = findMapValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof List<?> list) {
            for (Object child : list) {
                Map<String, Object> found = findMapValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<Object> findListValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            for (Object child : map.values()) {
                List<Object> found = findListValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof List<?> list) {
            for (Object child : list) {
                List<Object> found = findListValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private Integer toInteger(String value, Integer fallback) {
        try {
            return isBlank(value) ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean containsPoi(List<PoiCandidateDTO> values, String poiId) {
        return values.stream().anyMatch(value -> value.getPoiId().equals(poiId));
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? valueOrEmpty(fallback) : value.trim();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
