package com.platform.core.infrastructure.numbering;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class NumberingService {

    private final JdbcTemplate jdbc;

    public NumberingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String codeKbn, String tenantId) {
        return collect(codeKbn, "", tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String collect(String codeKbn, String fixedKey, String tenantId) {
        Map<String, Object> def;
        try {
            def = jdbc.queryForMap(
                    "SELECT format_sentence, recycle_division, zero_insert, seq_id_digit, " +
                    "       date_format_sentence, min_value, max_value, step_value, seq_id " +
                    "  FROM core_numbering_management WHERE code_kbn = ? LIMIT 1",
                    codeKbn);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Numbering definition not found: " + codeKbn);
        }

        String format = (String) def.get("format_sentence");
        int recycle = ((Number) def.get("recycle_division")).intValue();
        String zeroInsert = (String) def.getOrDefault("zero_insert", "0");
        int digit = ((Number) def.getOrDefault("seq_id_digit", 6)).intValue();
        String dateFormatSentence = (String) def.get("date_format_sentence");
        long minVal = ((Number) def.getOrDefault("min_value", 1L)).longValue();
        long maxVal = ((Number) def.getOrDefault("max_value", Long.MAX_VALUE)).longValue();
        long step = ((Number) def.getOrDefault("step_value", 1L)).longValue();

        String datePart = computeDatePart(recycle, dateFormatSentence);
        String expandedKey = format
                .replace("[%K]", fixedKey == null ? "" : fixedKey)
                .replace("[%D]", datePart);

        long nextSeq;
        if (fixedKey == null || fixedKey.isEmpty()) {
            long current = ((Number) def.get("seq_id")).longValue();
            nextSeq = current + step;
            if (nextSeq > maxVal) nextSeq = minVal;
            jdbc.update("UPDATE core_numbering_management SET seq_id = ? WHERE code_kbn = ?",
                    nextSeq, codeKbn);
        } else {
            jdbc.update(
                    "INSERT INTO core_numbering_key (tenant_id, code_kbn, numbering_key, seq_id) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (tenant_id, code_kbn, numbering_key) DO NOTHING",
                    tenantId == null ? "default" : tenantId, codeKbn, expandedKey, minVal - step);
            Long current = jdbc.queryForObject(
                    "SELECT seq_id FROM core_numbering_key " +
                    "WHERE tenant_id = ? AND code_kbn = ? AND numbering_key = ? FOR UPDATE",
                    Long.class,
                    tenantId == null ? "default" : tenantId, codeKbn, expandedKey);
            nextSeq = (current == null ? minVal - step : current) + step;
            if (nextSeq > maxVal) nextSeq = minVal;
            jdbc.update(
                    "UPDATE core_numbering_key SET seq_id = ? " +
                    "WHERE tenant_id = ? AND code_kbn = ? AND numbering_key = ?",
                    nextSeq,
                    tenantId == null ? "default" : tenantId, codeKbn, expandedKey);
        }

        String seqStr = padSeq(nextSeq, digit, zeroInsert);
        return expandedKey.replace("[%]", seqStr);
    }

    private static String computeDatePart(int recycleDivision, String dateFormatSentence) {
        if (dateFormatSentence != null && !dateFormatSentence.isBlank()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormatSentence));
        }
        return switch (recycleDivision) {
            case 1 -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case 2 -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case 3 -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
            default -> "";
        };
    }

    private static String padSeq(long seq, int digit, String zeroInsert) {
        String s = Long.toString(seq);
        if (s.length() >= digit) return s;
        char pad = (zeroInsert == null || zeroInsert.isEmpty()) ? '0' : zeroInsert.charAt(0);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < digit; i++) sb.append(pad);
        sb.append(s);
        return sb.toString();
    }
}
