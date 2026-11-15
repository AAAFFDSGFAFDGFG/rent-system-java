package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.common.JsonUtils;
import com.example.rentsystemjava.domain.House;
import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.domain.SystemConfig;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import com.example.rentsystemjava.dto.ContractDto;
import com.example.rentsystemjava.dto.ContractHistoryDto;
import com.example.rentsystemjava.dto.HouseDetailDto;
import com.example.rentsystemjava.dto.HouseSummaryDto;
import com.example.rentsystemjava.dto.HouseUpsertRequest;
import com.example.rentsystemjava.dto.LeaseProgressDto;
import com.example.rentsystemjava.dto.NotifyUserDto;
import com.example.rentsystemjava.dto.PaymentCalendarDto;
import com.example.rentsystemjava.repository.HouseRepository;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 房源服务：列表/详情/新增/编辑/软删除/小区楼栋下拉。
 * 软删除口径：所有查询过滤 deleted_at IS NULL；有生效合同禁止删除（2003）。
 */
@Service
public class HouseService {

    private final HouseRepository houseRepository;
    private final LeaseContractRepository contractRepository;
    private final MemberRepository memberRepository;
    private final SystemConfigRepository systemConfigRepository;

    public HouseService(HouseRepository houseRepository, LeaseContractRepository contractRepository,
                        MemberRepository memberRepository, SystemConfigRepository systemConfigRepository) {
        this.houseRepository = houseRepository;
        this.contractRepository = contractRepository;
        this.memberRepository = memberRepository;
        this.systemConfigRepository = systemConfigRepository;
    }

    // ---------------- 查询 ----------------

    /** 房源列表（B2）：status/community/building 过滤，按小区-楼栋-楼层-房号排序 */
    public List<HouseSummaryDto> list(String status, String community, String building) {
        List<House> houses = houseRepository.findAllByDeletedAtIsNull();
        Map<Long, LeaseContract> activeByHouse = activeContractMap();
        String statusFilter = (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) ? null : status;
        String communityFilter = (community == null || community.isBlank() || "all".equals(community)) ? null : community;
        String buildingFilter = (building == null || building.isBlank() || "all".equals(building)) ? null : building;

        return houses.stream()
                .filter(h -> statusFilter == null || h.getStatus().getValue().equals(statusFilter))
                .filter(h -> communityFilter == null || h.getCommunity().equals(communityFilter))
                .filter(h -> buildingFilter == null || h.getBuilding().equals(buildingFilter))
                .sorted(Comparator.comparing(House::getCommunity)
                        .thenComparing(House::getBuilding)
                        .thenComparing(House::getFloorNo)
                        .thenComparing(House::getRoomNo))
                .map(h -> toSummary(h, activeByHouse.get(h.getId())))
                .toList();
    }

    public List<String> communities() {
        return houseRepository.findDistinctCommunities();
    }

    public List<String> buildings(String community) {
        return houseRepository.findDistinctBuildings(community);
    }

    /** 房源详情（C1）：契约合同 + 租期进度 + 付款日历 + 负责通知 + 合同历史 */
    public HouseDetailDto detail(Long houseId, Long currentMemberId) {
        House house = requireHouse(houseId);
        LeaseContract active = contractRepository.findByHouseIdAndStatus(houseId, ContractStatus.ACTIVE).orElse(null);
        List<LeaseContract> history = contractRepository.findByHouseIdOrderByStartDateAsc(houseId).stream()
                .filter(c -> c.getStatus() == ContractStatus.TERMINATED)
                .toList();
        LocalDate today = LocalDate.now();

        LeaseProgressDto progress = active == null ? null : toProgress(active, today);
        PaymentCalendarDto calendar = active == null ? null : toPaymentCalendar(active, today);
        NotifyUserDto notifyUser = buildNotifyUser(currentMemberId);
        List<ContractHistoryDto> historyDtos = history.stream().map(HouseService::toHistory).toList();

        return new HouseDetailDto(
                house.getId(), house.getCommunity(), house.getBuilding(),
                house.getFloorNo().intValue(), house.getRoomNo(), house.getLayout(),
                house.getArea(), house.getFace(), house.getExpectedRent(),
                displayRent(house, active), house.getStatus().getValue(),
                JsonUtils.parseStringList(house.getTagsJson()),
                active == null ? null : toContract(active),
                progress, calendar, notifyUser, remindAdvanceDays(), historyDtos);
    }

    // ---------------- 写操作 ----------------

    @Transactional
    public HouseSummaryDto create(HouseUpsertRequest req) {
        validateUpsert(req);
        Short floorNo = req.floorNo() == null ? (short) 1 : req.floorNo().shortValue();
        String roomNo = blankToDefault(req.roomNo(), "A");
        if (houseRepository.existsByCommunityAndBuildingAndFloorNoAndRoomNoAndDeletedAtIsNull(
                req.community().trim(), req.building().trim(), floorNo, roomNo)) {
            throw new BusinessException(ErrorCode.HOUSE_LOCATION_DUPLICATE);
        }
        House house = new House();
        house.setCommunity(req.community().trim());
        house.setBuilding(req.building().trim());
        house.setFloorNo(floorNo);
        house.setRoomNo(roomNo);
        house.setLayout(blankToDefault(req.layout(), "两室一厅"));
        house.setArea(req.area() == null ? new BigDecimal("60.00") : req.area());
        house.setFace(blankToDefault(req.face(), "南"));
        house.setExpectedRent(req.expectedRent());
        house.setStatus(HouseStatus.VACANT);
        houseRepository.save(house);
        return toSummary(house, null);
    }

    @Transactional
    public HouseSummaryDto update(Long houseId, HouseUpsertRequest req) {
        validateUpsert(req);
        House house = requireHouse(houseId);
        Short floorNo = req.floorNo() == null ? house.getFloorNo() : req.floorNo().shortValue();
        String roomNo = blankToDefault(req.roomNo(), house.getRoomNo());
        if (houseRepository.existsSameLocationExcludingId(houseId, req.community().trim(),
                req.building().trim(), floorNo, roomNo)) {
            throw new BusinessException(ErrorCode.HOUSE_LOCATION_DUPLICATE);
        }
        // 只改基础信息：不改 status / 生效合同 / id
        house.setCommunity(req.community().trim());
        house.setBuilding(req.building().trim());
        house.setFloorNo(floorNo);
        house.setRoomNo(roomNo);
        if (req.layout() != null) house.setLayout(req.layout());
        if (req.area() != null) house.setArea(req.area());
        if (req.face() != null) house.setFace(req.face());
        if (req.expectedRent() != null) house.setExpectedRent(req.expectedRent());
        houseRepository.save(house);
        return toSummary(house, contractRepository.findByHouseIdAndStatus(houseId, ContractStatus.ACTIVE).orElse(null));
    }

    /** 软删除：有生效合同拒绝（2003），写 deleted_at 不物理删行 */
    @Transactional
    public void delete(Long houseId) {
        House house = requireHouse(houseId);
        if (contractRepository.findByHouseIdAndStatus(houseId, ContractStatus.ACTIVE).isPresent()) {
            throw new BusinessException(ErrorCode.HOUSE_HAS_ACTIVE_CONTRACT);
        }
        house.setDeletedAt(LocalDateTime.now());
        houseRepository.save(house);
    }

    // ---------------- 供其他服务复用 ----------------

    public House requireHouse(Long houseId) {
        return houseRepository.findByIdAndDeletedAtIsNull(houseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOUSE_NOT_FOUND));
    }

    public Map<Long, LeaseContract> activeContractMap() {
        return contractRepository.findAllByStatus(ContractStatus.ACTIVE).stream()
                .collect(Collectors.toMap(LeaseContract::getHouseId, Function.identity()));
    }

    public static BigDecimal displayRent(House house, LeaseContract active) {
        return active != null ? active.getActualRent() : house.getExpectedRent();
    }

    /** 地址展示串：小区 楼栋 楼层F-房号 */
    public static String addressOf(House house) {
        return house.getCommunity() + " " + house.getBuilding() + " " + house.getFloorNo() + "F-" + house.getRoomNo();
    }

    public HouseSummaryDto toSummary(House house, LeaseContract active) {
        return new HouseSummaryDto(
                house.getId(), house.getCommunity(), house.getBuilding(),
                house.getFloorNo().intValue(), house.getRoomNo(), house.getLayout(),
                house.getArea(), house.getFace(), house.getExpectedRent(),
                displayRent(house, active), house.getStatus().getValue(),
                active == null ? null : active.getTenantName(),
                JsonUtils.parseStringList(house.getTagsJson()));
    }

    // ---------------- 私有 ----------------

    private static ContractDto toContract(LeaseContract c) {
        return new ContractDto(c.getId(), c.getActualRent(), c.getPaymentMode(), c.getDeposit(),
                c.getStartDate(), c.getEndDate(), c.getPayDay().intValue(),
                c.getElectricStart(), c.getWaterStart(), c.getTenantName(), c.getTenantPhone(), c.getNote());
    }

    private static ContractHistoryDto toHistory(LeaseContract c) {
        return new ContractHistoryDto(c.getId(), c.getTenantName(), c.getActualRent(),
                c.getStartDate(), c.getEndDate(),
                c.getEndReason() == null ? null : c.getEndReason().getValue(),
                c.getTerminatedAt(), c.getForfeitedDeposit());
    }

    private LeaseProgressDto toProgress(LeaseContract c, LocalDate today) {
        long total = ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate());
        long elapsed = ChronoUnit.DAYS.between(c.getStartDate(), today);
        int percent = total <= 0 ? 100 : (int) Math.round(elapsed * 100.0 / total);
        percent = Math.max(0, Math.min(100, percent));
        long daysToExpire = ChronoUnit.DAYS.between(today, c.getEndDate());
        return new LeaseProgressDto(c.getStartDate(), c.getEndDate(), today, percent, daysToExpire);
    }

    private PaymentCalendarDto toPaymentCalendar(LeaseContract c, LocalDate today) {
        int year = today.getYear();
        int month = today.getMonthValue();
        int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
        // DayOfWeek: MON=1..SUN=7；换算成日历首列 SUN=0..SAT=6
        int leadingBlanks = today.withDayOfMonth(1).getDayOfWeek().getValue() % 7;
        // 付款日 1-31，小月无对应日期时取当月最后一日（PRD 口径）
        int effectivePayDay = Math.min(c.getPayDay().intValue(), daysInMonth);
        List<PaymentCalendarDto.DayCell> days = new java.util.ArrayList<>(leadingBlanks + daysInMonth);
        for (int i = 0; i < leadingBlanks; i++) {
            days.add(new PaymentCalendarDto.DayCell(0, false, false));
        }
        for (int d = 1; d <= daysInMonth; d++) {
            days.add(new PaymentCalendarDto.DayCell(d, d == today.getDayOfMonth(), d == effectivePayDay));
        }
        return new PaymentCalendarDto(year, month, c.getPayDay().intValue(), today.getDayOfMonth(), days);
    }

    private NotifyUserDto buildNotifyUser(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        return member == null ? null : new NotifyUserDto(member.getUsername(), member.getPhone());
    }

    private int remindAdvanceDays() {
        return systemConfigRepository.findByConfigKey("remind_advance_days")
                .map(SystemConfig::getConfigValue)
                .map(Integer::parseInt)
                .orElse(7);
    }

    private void validateUpsert(HouseUpsertRequest req) {
        if (req.community() == null || req.community().isBlank()
                || req.building() == null || req.building().isBlank()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_MISSING, "小区与楼栋必填");
        }
        if (req.expectedRent() == null || req.expectedRent().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.NUMBER_FORMAT_INVALID, "预期月租须大于等于 0");
        }
    }

    private static String blankToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s.trim();
    }
}
