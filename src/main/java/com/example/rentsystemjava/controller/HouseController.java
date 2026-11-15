package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.HouseDetailDto;
import com.example.rentsystemjava.dto.HouseSummaryDto;
import com.example.rentsystemjava.dto.HouseUpsertRequest;
import com.example.rentsystemjava.dto.LeaseRequest;
import com.example.rentsystemjava.dto.RenewRequest;
import com.example.rentsystemjava.dto.VacateRequest;
import com.example.rentsystemjava.service.ContractService;
import com.example.rentsystemjava.service.HouseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 房源控制层（薄）：列表/下拉/详情/CRUD/登记出租/退租/续租。
 * memberId 由 AuthInterceptor 放入 request attribute。
 */
@RestController
public class HouseController {

    private final HouseService houseService;
    private final ContractService contractService;

    public HouseController(HouseService houseService, ContractService contractService) {
        this.houseService = houseService;
        this.contractService = contractService;
    }

    /** B2 房源列表（首页网格 + 配置页房源管理共用） */
    @GetMapping("/api/houses")
    public ApiResult<List<HouseSummaryDto>> list(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") String community,
            @RequestParam(defaultValue = "all") String building) {
        return ApiResult.ok(houseService.list(status, community, building));
    }

    /** B3 小区下拉 */
    @GetMapping("/api/communities")
    public ApiResult<List<String>> communities() {
        return ApiResult.ok(houseService.communities());
    }

    /** B4 楼栋级联 */
    @GetMapping("/api/communities/{community}/buildings")
    public ApiResult<List<String>> buildings(@PathVariable String community) {
        return ApiResult.ok(houseService.buildings(community));
    }

    /** C1 房源详情（已租/空置通用） */
    @GetMapping("/api/houses/{houseId}")
    public ApiResult<HouseDetailDto> detail(@PathVariable Long houseId, HttpServletRequest request) {
        return ApiResult.ok(houseService.detail(houseId, currentMemberId(request)));
    }

    /** C2 新增房源 */
    @PostMapping("/api/houses")
    public ApiResult<HouseSummaryDto> create(@RequestBody HouseUpsertRequest body) {
        return ApiResult.ok(houseService.create(body));
    }

    /** C3 编辑房源基础信息 */
    @PutMapping("/api/houses/{houseId}")
    public ApiResult<HouseSummaryDto> update(@PathVariable Long houseId, @RequestBody HouseUpsertRequest body) {
        return ApiResult.ok(houseService.update(houseId, body));
    }

    /** C4 删除房源（软删除；有生效合同 → 2003） */
    @DeleteMapping("/api/houses/{houseId}")
    public ApiResult<Map<String, Object>> delete(@PathVariable Long houseId) {
        houseService.delete(houseId);
        return ApiResult.ok(Map.of("id", houseId, "deleted", true));
    }

    /** C5 登记出租（空置→已租） */
    @PostMapping("/api/houses/{houseId}/lease")
    public ApiResult<HouseDetailDto> lease(@PathVariable Long houseId, @RequestBody LeaseRequest body,
                                           HttpServletRequest request) {
        return ApiResult.ok(contractService.lease(houseId, body, currentMemberId(request)));
    }

    /** C6 退租（未到期首次请求 → 409/2006 违约预览；确认后带 confirmBreach=true 重发） */
    @PostMapping("/api/houses/{houseId}/vacate")
    public ApiResult<HouseDetailDto> vacate(@PathVariable Long houseId,
                                            @RequestBody(required = false) VacateRequest body,
                                            HttpServletRequest request) {
        boolean confirmBreach = body != null && body.confirmed();
        return ApiResult.ok(contractService.vacate(houseId, confirmBreach, currentMemberId(request)));
    }

    /** C7 续租（旧合同终止 + 新合同生效） */
    @PostMapping("/api/houses/{houseId}/renew")
    public ApiResult<HouseDetailDto> renew(@PathVariable Long houseId, @RequestBody RenewRequest body,
                                           HttpServletRequest request) {
        return ApiResult.ok(contractService.renew(houseId, body, currentMemberId(request)));
    }

    private static Long currentMemberId(HttpServletRequest request) {
        return (Long) request.getAttribute("memberId");
    }
}
