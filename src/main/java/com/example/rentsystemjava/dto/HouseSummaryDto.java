package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.util.List;

/** 房源列表项（B2，首页网格与配置页房源管理共用） */
public record HouseSummaryDto(
        Long id,
        String community,
        String building,
        Integer floorNo,
        String roomNo,
        String layout,
        BigDecimal area,
        String face,
        BigDecimal expectedRent,
        BigDecimal displayRent,
        String status,
        String tenantName,
        List<String> tags) {
}
