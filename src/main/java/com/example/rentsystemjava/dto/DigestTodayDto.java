package com.example.rentsystemjava.dto;

/** 今日日报卡（E1）：摘要实时算，正文取当日已生成版本；未生成时实时渲染预览 */
public record DigestTodayDto(
        String reportDate,
        SummaryDto summary,
        String contentText,
        boolean generated,
        String channel,
        long sentMemberCount) {

    public record SummaryDto(
            long vacantCount,
            long upcomingCount) {
    }
}
