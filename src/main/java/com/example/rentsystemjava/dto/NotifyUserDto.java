package com.example.rentsystemjava.dto;

/** 详情「负责通知」行（取当前登录成员） */
public record NotifyUserDto(
        String username,
        String phone) {
}
