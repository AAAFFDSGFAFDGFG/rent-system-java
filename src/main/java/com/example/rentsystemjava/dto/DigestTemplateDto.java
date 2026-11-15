package com.example.rentsystemjava.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/** 每日分发文案模板（D1）。renderedPreview 按今日数据实时渲染。 */
public record DigestTemplateDto(
        Long id,
        String name,
        String contentText,
        List<String> variables,
        boolean isDefault,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt,
        String renderedPreview,
        Boolean restored) {
}
