package com.feijimiao.xianyuassistant.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 */
public final class DateTimeUtils {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_MILLIS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private DateTimeUtils() {
    }

    public static String currentShanghaiTime() {
        return currentShanghaiDateTime().format(DATETIME_FORMATTER);
    }

    public static String currentShanghaiTimeWithMillis() {
        return currentShanghaiDateTime().format(DATETIME_MILLIS_FORMATTER);
    }

    public static LocalDateTime currentShanghaiDateTime() {
        return LocalDateTime.now(SHANGHAI_ZONE);
    }
}
