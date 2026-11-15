package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.NotificationRecordDto;
import com.example.rentsystemjava.service.NotificationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F4 通知记录列表 / F5 补发 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;

    public NotificationController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResult<List<NotificationRecordDto>> list(@RequestParam(required = false) Integer limit) {
        return ApiResult.ok(queryService.list(limit));
    }

    @PostMapping("/{id}/resend")
    public ApiResult<NotificationRecordDto> resend(@PathVariable Long id) {
        return ApiResult.ok(queryService.resend(id));
    }
}
