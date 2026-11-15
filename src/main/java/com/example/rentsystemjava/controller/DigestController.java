package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.DigestTodayDto;
import com.example.rentsystemjava.service.DigestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** E1 今日日报卡（摘要实时、正文取已生成版本或实时渲染预览） */
@RestController
public class DigestController {

    private final DigestService digestService;

    public DigestController(DigestService digestService) {
        this.digestService = digestService;
    }

    @GetMapping("/api/digests/today")
    public ApiResult<DigestTodayDto> today() {
        return ApiResult.ok(digestService.today());
    }
}
