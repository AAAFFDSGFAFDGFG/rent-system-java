package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.domain.House;
import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import com.example.rentsystemjava.domain.enums.ReminderType;
import com.example.rentsystemjava.dto.CalendarDto;
import com.example.rentsystemjava.dto.CalendarDayDto;
import com.example.rentsystemjava.dto.UpcomingItemDto;
import com.example.rentsystemjava.repository.HouseRepository;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 提醒日历（F1/F2/F3，方案 B）：全部由生效合同实时计算，不查 reminder_record。
 * pay 事件：该月付款日（pay_day 大于当月天数取当月最后一日）；expire 事件：合同 end_date。
 * 未来 7 天临期：下一付款日或到期日落在 [今日, 今日+7]。
 */
@Service
public class CalendarService {

    private final LeaseContractRepository contractRepository;
    private final HouseRepository houseRepository;

    public CalendarService(LeaseContractRepository contractRepository, HouseRepository houseRepository) {
        this.contractRepository = contractRepository;
        this.houseRepository = houseRepository;
    }

    // ---------------- F1 月历 ----------------

    @Transactional(readOnly = true)
    public CalendarDto calendar(Integer year, Integer month) {
        if (year == null || month == null || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "year 与 month(1-12) 必填");
        }
        LocalDate monthDate = LocalDate.of(year, month, 1);
        int monthLength = monthDate.lengthOfMonth();
        LocalDate today = LocalDate.now();

        Map<Integer, CalendarDto.EventDayDto> days = new TreeMap<>();
        for (LeaseContract c : contractRepository.findAllByStatus(ContractStatus.ACTIVE)) {
            // pay 事件：本月付款日（1-31 小月兜底取当月最后一日）
            int payDay = Math.min(c.getPayDay(), monthLength);
            addEvent(days, payDay, "pay", c.getHouseId());
            // expire 事件：合同到期日落在该月
            if (c.getEndDate().getYear() == year && c.getEndDate().getMonthValue() == month) {
                addEvent(days, c.getEndDate().getDayOfMonth(), "expire", c.getHouseId());
            }
        }
        return new CalendarDto(year, month, today.toString(), new ArrayList<>(days.values()));
    }

    private void addEvent(Map<Integer, CalendarDto.EventDayDto> days, int day, String type, Long houseId) {
        CalendarDto.EventDayDto dto = days.computeIfAbsent(day,
                k -> new CalendarDto.EventDayDto(k, new ArrayList<>(), new ArrayList<>()));
        if (!dto.types().contains(type)) {
            dto.types().add(type);
        }
        if (!dto.houseIds().contains(houseId)) {
            dto.houseIds().add(houseId);
        }
    }

    // ---------------- F2 点日期弹窗 ----------------

    @Transactional(readOnly = true)
    public CalendarDayDto dayItems(String dateText) {
        LocalDate date;
        try {
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "日期格式须为 yyyy-MM-dd");
        }
        int monthLength = date.lengthOfMonth();
        List<CalendarDayDto.ItemDto> items = new ArrayList<>();
        for (LeaseContract c : contractRepository.findAllByStatus(ContractStatus.ACTIVE)) {
            int payDay = Math.min(c.getPayDay(), monthLength);
            if (payDay == date.getDayOfMonth()) {
                items.add(toItem(c, date, "pay"));
            }
            if (c.getEndDate().equals(date)) {
                items.add(toItem(c, date, "expire"));
            }
        }
        return new CalendarDayDto(date, items);
    }

    private CalendarDayDto.ItemDto toItem(LeaseContract c, LocalDate date, String type) {
        House house = houseRepository.findByIdAndDeletedAtIsNull(c.getHouseId()).orElse(null);
        String houseName = house == null ? "已删除房源#" + c.getHouseId() : HouseService.addressOf(house);
        return new CalendarDayDto.ItemDto(c.getHouseId(), houseName, type,
                c.getTenantName(), c.getActualRent(), c.getPaymentMode());
    }

    // ---------------- F3 未来 7 天临期（日报渲染复用） ----------------

    @Transactional(readOnly = true)
    public List<UpcomingItemDto> upcoming() {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(7);
        Map<Long, House> houses = houseRepository.findAllByDeletedAtIsNull().stream()
                .collect(java.util.stream.Collectors.toMap(House::getId, h -> h));

        List<UpcomingItemDto> result = new ArrayList<>();
        for (LeaseContract c : contractRepository.findAllByStatus(ContractStatus.ACTIVE)) {
            House house = houses.get(c.getHouseId());
            String houseName = house == null ? "已删除房源#" + c.getHouseId() : HouseService.addressOf(house);
            // 付款事项：下一付款日（本月或下月，小月取最后一日）
            LocalDate nextPay = nextPaymentDate(c.getPayDay(), today);
            if (!nextPay.isAfter(windowEnd)) {
                result.add(toItem(c, houseName, ReminderType.PAY.getValue(), nextPay, today));
            }
            // 到期事项：end_date 落在窗口内（含今天）
            if (!c.getEndDate().isBefore(today) && !c.getEndDate().isAfter(windowEnd)) {
                result.add(toItem(c, houseName, ReminderType.EXPIRE.getValue(), c.getEndDate(), today));
            }
        }
        result.sort(Comparator.comparingLong(UpcomingItemDto::daysLeft));
        return result;
    }

    private UpcomingItemDto toItem(LeaseContract c, String houseName, String type, LocalDate date, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, date);
        return new UpcomingItemDto(c.getHouseId(), c.getId(), houseName, type, date, daysLeft,
                c.getTenantName(), daysLeft <= 3);
    }

    /** 下一付款日：本月付款日 ≥ 今日取本月（小月取最后一日）；否则取下月 */
    public static LocalDate nextPaymentDate(byte payDay, LocalDate today) {
        int day = Math.min(payDay, today.lengthOfMonth());
        LocalDate thisMonth = today.withDayOfMonth(day);
        if (!thisMonth.isBefore(today)) {
            return thisMonth;
        }
        LocalDate nextMonth = today.plusMonths(1);
        return nextMonth.withDayOfMonth(Math.min(payDay, nextMonth.lengthOfMonth()));
    }
}
