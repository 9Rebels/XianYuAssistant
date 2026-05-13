package com.feijimiao.xianyuassistant.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CaptchaCookieMergeService {
    private static final Pattern SET_COOKIE_PAIR = Pattern.compile("^\\s*([^=;\\s]+)=([^;]*)");
    private static final List<String> PROTECTED_FIELDS = List.of(
            "unb",
            "sgcookie",
            "cookie2",
            "_m_h5_tk",
            "_m_h5_tk_enc",
            "t",
            "cna",
            "havana_lgc2_77",
            "_tb_token_"
    );
    private static final List<String> REQUIRED_FIELDS = List.of(
            "unb",
            "sgcookie",
            "cookie2",
            "_m_h5_tk",
            "_m_h5_tk_enc",
            "t"
    );

    public CookieMergeResult merge(String existingCookieText,
                                   Map<String, String> browserCookies,
                                   Map<String, String> responseCookies) {
        Map<String, String> existing = parseCookieText(existingCookieText);
        Map<String, String> incoming = mergeIncoming(browserCookies, responseCookies);
        boolean accountSwitched = isAccountSwitched(existing, incoming);
        Map<String, String> merged = accountSwitched ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        merged.putAll(incoming);

        List<String> wouldRemoveFields = missingFromIncoming(existing, incoming);
        List<String> removedFields = accountSwitched ? wouldRemoveFields : List.of();
        List<String> preservedFields = accountSwitched ? List.of() : wouldRemoveFields;
        List<String> preservedProtectedFields = accountSwitched
                ? List.of()
                : filterProtectedPreserved(wouldRemoveFields, existing);

        return new CookieMergeResult(
                merged,
                changedFields(existing, incoming),
                newFields(existing, incoming),
                wouldRemoveFields,
                removedFields,
                preservedFields,
                preservedProtectedFields,
                missingFields(merged, PROTECTED_FIELDS),
                missingFields(merged, REQUIRED_FIELDS),
                missingFields(incoming, PROTECTED_FIELDS),
                missingFields(incoming, REQUIRED_FIELDS),
                accountSwitched
        );
    }

    public Map<String, String> parseCookieText(String cookieText) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (cookieText == null || cookieText.isBlank()) {
            return cookies;
        }
        Arrays.stream(cookieText.split(";\\s*"))
                .map(String::trim)
                .filter(part -> part.contains("="))
                .forEach(part -> putCookiePair(cookies, part));
        return cookies;
    }

    public String formatCookieText(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        cookies.forEach((name, value) -> appendCookie(builder, name, value));
        return builder.toString();
    }

    public Map<String, String> extractSetCookieUpdates(List<String> setCookieHeaders) {
        Map<String, String> updates = new LinkedHashMap<>();
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return updates;
        }
        for (String header : setCookieHeaders) {
            Matcher matcher = SET_COOKIE_PAIR.matcher(header == null ? "" : header);
            if (!matcher.find()) {
                continue;
            }
            String value = matcher.group(2).trim();
            if (!value.isEmpty()) {
                updates.put(matcher.group(1).trim(), value);
            }
        }
        return updates;
    }

    private Map<String, String> mergeIncoming(Map<String, String> browserCookies,
                                              Map<String, String> responseCookies) {
        Map<String, String> incoming = new LinkedHashMap<>();
        copyNonBlank(incoming, browserCookies);
        copyNonBlank(incoming, responseCookies);
        return incoming;
    }

    private void copyNonBlank(Map<String, String> target, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                target.put(name.trim(), value.trim());
            }
        });
    }

    private void putCookiePair(Map<String, String> cookies, String part) {
        int index = part.indexOf('=');
        String name = part.substring(0, index).trim();
        String value = part.substring(index + 1).trim();
        if (!name.isBlank() && !value.isBlank()) {
            cookies.put(name, value);
        }
    }

    private void appendCookie(StringBuilder builder, String name, String value) {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(name).append("=").append(value);
    }

    private boolean isAccountSwitched(Map<String, String> existing, Map<String, String> incoming) {
        String existingUnb = trimToEmpty(existing.get("unb"));
        String incomingUnb = trimToEmpty(incoming.get("unb"));
        return !existingUnb.isEmpty() && !incomingUnb.isEmpty() && !existingUnb.equals(incomingUnb);
    }

    private List<String> missingFromIncoming(Map<String, String> existing, Map<String, String> incoming) {
        List<String> fields = new ArrayList<>();
        existing.keySet().forEach(key -> {
            if (!incoming.containsKey(key)) {
                fields.add(key);
            }
        });
        return fields;
    }

    private List<String> filterProtectedPreserved(List<String> fields, Map<String, String> existing) {
        List<String> preserved = new ArrayList<>();
        fields.forEach(field -> {
            if (PROTECTED_FIELDS.contains(field) && existing.get(field) != null) {
                preserved.add(field);
            }
        });
        return preserved;
    }

    private List<String> changedFields(Map<String, String> existing, Map<String, String> incoming) {
        List<String> changed = new ArrayList<>();
        incoming.forEach((key, value) -> {
            if (existing.containsKey(key) && !value.equals(existing.get(key))) {
                changed.add(key);
            }
        });
        return changed;
    }

    private List<String> newFields(Map<String, String> existing, Map<String, String> incoming) {
        List<String> added = new ArrayList<>();
        incoming.keySet().forEach(key -> {
            if (!existing.containsKey(key)) {
                added.add(key);
            }
        });
        return added;
    }

    private List<String> missingFields(Map<String, String> cookies, List<String> requiredNames) {
        List<String> missing = new ArrayList<>();
        requiredNames.forEach(name -> {
            if (trimToEmpty(cookies.get(name)).isEmpty()) {
                missing.add(name);
            }
        });
        return missing;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    @AllArgsConstructor
    public static class CookieMergeResult {
        private Map<String, String> mergedCookies;
        private List<String> changedFields;
        private List<String> newFields;
        private List<String> wouldRemoveFields;
        private List<String> removedFields;
        private List<String> preservedFields;
        private List<String> preservedProtectedFields;
        private List<String> missingProtectedFields;
        private List<String> missingRequiredFields;
        private List<String> incomingMissingProtectedFields;
        private List<String> incomingMissingRequiredFields;
        private boolean accountSwitched;
    }
}
