package com.example.rentsystemjava.common;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** JSON 轻量工具（Jackson 3）：tags_json / snapshots / variables_json 均为 JSON 字符串列 */
public final class JsonUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonUtils() {
    }

    /** JSON 数组字符串 → List&lt;String&gt;；null/非法返回空列表 */
    public static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** List&lt;String&gt; → JSON 数组字符串；null/空返回 null */
    public static String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(list);
    }

    /** 对象 → JSON 字符串（快照落库用） */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return MAPPER.writeValueAsString(obj);
    }
}
