package com.example.rentsystemjava.dto;

/** 首页四张指标卡（B1） */
public record DashboardStatsDto(
        long totalHouses,
        long rentedHouses,
        long vacantHouses,
        long pendingCollectionCount,
        long paymentReminderCount,
        long communityCount) {
}
