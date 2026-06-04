package com.platform.system.dict.service;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * (De)serialises the {@code *_i18n} jsonb columns (stored as raw JSON strings,
 * same approach as {@code MenuEntity.titleI18n}). Jackson 3 {@link JsonMapper}.
 */
@Component
public class DictJsonCodec {

    private static final TypeReference<Map<String, String>> I18N_MAP = new TypeReference<>() {};

    private final JsonMapper jsonMapper;

    public DictJsonCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Raw JSON → map; null/blank → null; malformed → null (never fails the read). */
    public Map<String, String> parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return jsonMapper.readValue(raw, I18N_MAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** Map → JSON; null/empty → null (we want NULL in the column, not "{}"). */
    public String serialize(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return jsonMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Invalid i18n payload");
        }
    }
}
