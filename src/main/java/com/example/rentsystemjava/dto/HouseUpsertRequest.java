package com.example.rentsystemjava.dto;

import java.math.BigDecimal;

/** 新增/编辑房源请求（C2/C3）。不接受 status/contract。 */
public record HouseUpsertRequest(
        String community,
        String building,
        Integer floorNo,
        String roomNo,
        String layout,
        BigDecimal area,
        String face,
        BigDecimal expectedRent) {
}
