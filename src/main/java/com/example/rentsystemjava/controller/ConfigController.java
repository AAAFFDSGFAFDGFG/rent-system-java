package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.domain.SystemConfig;
import com.example.rentsystemjava.dto.DigestTemplateDto;
import com.example.rentsystemjava.dto.PreviewResultDto;
import com.example.rentsystemjava.dto.SystemConfigDto;
import com.example.rentsystemjava.repository.SystemConfigRepository;
import com.example.rentsystemjava.service.DigestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 配置控制层：模板 D1-D4（GET/PUT/preview/reset）+ 系统配置 D5（只读）。
 */
@RestController
public class ConfigController {

    private final DigestService digestService;
    private final SystemConfigRepository systemConfigRepository;

    public ConfigController(DigestService digestService, SystemConfigRepository systemConfigRepository) {
        this.digestService = digestService;
        this.systemConfigRepository = systemConfigRepository;
    }

    @GetMapping("/api/config/digest-template")
    public ApiResult<DigestTemplateDto> getTemplate() {
        return ApiResult.ok(digestService.getTemplate());
    }

    @PutMapping("/api/config/digest-template")
    public ApiResult<DigestTemplateDto> saveTemplate(@RequestBody Map<String, String> body) {
        return ApiResult.ok(digestService.saveTemplate(body.get("contentText")));
    }

    @PostMapping("/api/config/digest-template/preview")
    public ApiResult<PreviewResultDto> preview(@RequestBody Map<String, String> body) {
        return ApiResult.ok(new PreviewResultDto(digestService.preview(body.get("contentText"))));
    }

    @PostMapping("/api/config/digest-template/reset")
    public ApiResult<DigestTemplateDto> resetTemplate() {
        return ApiResult.ok(digestService.resetTemplate());
    }

    /** D5 系统配置（只读；PUT /api/config/system 为可选接口本轮不实现） */
    @GetMapping("/api/config/system")
    public ApiResult<SystemConfigDto> system() {
        return ApiResult.ok(new SystemConfigDto(
                configValue("digest_send_time", "09:00"),
                parseIntConfig("remind_advance_days", 7),
                "1".equals(configValue("send_on_empty_day", "0"))));
    }

    private String configValue(String key, String def) {
        return systemConfigRepository.findByConfigKey(key).map(SystemConfig::getConfigValue).orElse(def);
    }

    private Integer parseIntConfig(String key, int def) {
        try {
            return Integer.parseInt(configValue(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
