package com.example.rentsystemjava.dto;

/** 系统配置（D5）：推送配置页展示 + 详情抽屉「提前 7 天」只读来源 */
public record SystemConfigDto(
        String digestSendTime,
        Integer remindAdvanceDays,
        Boolean sendOnEmptyDay) {
}
