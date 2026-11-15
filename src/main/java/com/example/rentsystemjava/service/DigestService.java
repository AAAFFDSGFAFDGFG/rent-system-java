package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.JsonUtils;
import com.example.rentsystemjava.domain.DigestReport;
import com.example.rentsystemjava.domain.DigestTemplate;
import com.example.rentsystemjava.domain.House;
import com.example.rentsystemjava.domain.ReminderRecord;
import com.example.rentsystemjava.domain.SystemConfig;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import com.example.rentsystemjava.domain.enums.DigestStatus;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import com.example.rentsystemjava.domain.enums.NotificationStatus;
import com.example.rentsystemjava.domain.enums.ReminderType;
import com.example.rentsystemjava.dto.DigestTodayDto;
import com.example.rentsystemjava.dto.DigestTemplateDto;
import com.example.rentsystemjava.dto.UpcomingItemDto;
import com.example.rentsystemjava.repository.DigestReportRepository;
import com.example.rentsystemjava.service.HouseService;
import com.example.rentsystemjava.repository.DigestTemplateRepository;
import com.example.rentsystemjava.repository.HouseRepository;
import com.example.rentsystemjava.repository.NotificationRecordRepository;
import com.example.rentsystemjava.repository.ReminderRecordRepository;
import com.example.rentsystemjava.repository.SystemConfigRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 日报服务：正文渲染（5 变量、无人物信息）+ 今日日报查询 + 模板配置。
 * 渲染规则（database-design.md §5）：
 * {日期}=yyyy-MM-dd；{空置数量}=vacant 未删除计数；
 * {空置房源清单}=· 小区 楼栋 楼层F-房号｜户型 面积㎡｜预期 ¥x/月（无则 · 暂无）；
 * {临期事项清单}=· 地址｜付款/到期｜日期（剩 N 天）（无则 · 暂无）。
 */
@Service
public class DigestService {

    public static final String DIGEST_TYPE = "daily";
    /** 与 seed-data.sql 默认模板一致的内置默认正文（「恢复默认」用） */
    public static final String DEFAULT_TEMPLATE_TEXT =
            "【家庭房源日报】{日期}\n"
            + "📢 空置房源（{空置数量} 套）\n"
            + "{空置房源清单}\n"
            + "⏰ 未来 7 天临期（{临期数量} 项）\n"
            + "{临期事项清单}";

    private final DigestTemplateRepository templateRepository;
    private final DigestReportRepository reportRepository;
    private final HouseRepository houseRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final ReminderRecordRepository reminderRecordRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final CalendarService calendarService;

    public DigestService(DigestTemplateRepository templateRepository, DigestReportRepository reportRepository,
                         HouseRepository houseRepository, NotificationRecordRepository notificationRecordRepository,
                         ReminderRecordRepository reminderRecordRepository,
                         SystemConfigRepository systemConfigRepository, CalendarService calendarService) {
        this.templateRepository = templateRepository;
        this.reportRepository = reportRepository;
        this.houseRepository = houseRepository;
        this.notificationRecordRepository = notificationRecordRepository;
        this.reminderRecordRepository = reminderRecordRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.calendarService = calendarService;
    }

    // ---------------- 正文渲染 ----------------

    /** 用给定模板 + 今日实时数据渲染正文（预览与未生成日报共用） */
    public String render(String templateText, LocalDate date, List<House> vacantHouses, List<UpcomingItemDto> upcoming) {
        String vacantText = vacantHouses.isEmpty() ? "· 暂无"
                : vacantHouses.stream()
                        .map(h -> "· " + h.getCommunity() + " " + h.getBuilding() + " " + h.getFloorNo()
                                + "F-" + h.getRoomNo() + "｜" + h.getLayout() + " " + stripTrailingZeros(h.getArea())
                                + "㎡｜预期 ¥" + stripTrailingZeros(h.getExpectedRent()) + "/月")
                        .reduce((a, b) -> a + "\n" + b).orElse("· 暂无");
        String upcomingText = upcoming.isEmpty() ? "· 暂无"
                : upcoming.stream()
                        .map(i -> "· " + i.houseName() + "｜" + ("pay".equals(i.type()) ? "付款" : "到期")
                                + "｜" + i.date() + "（剩 " + i.daysLeft() + " 天）")
                        .reduce((a, b) -> a + "\n" + b).orElse("· 暂无");

        return templateText
                .replace("{日期}", date.toString())
                .replace("{空置数量}", String.valueOf(vacantHouses.size()))
                .replace("{空置房源清单}", vacantText)
                .replace("{临期数量}", String.valueOf(upcoming.size()))
                .replace("{临期事项清单}", upcomingText);
    }

    // ---------------- 今日数据快照 ----------------

    public List<House> vacantHouses() {
        return houseRepository.findAllByDeletedAtIsNullAndStatus(HouseStatus.VACANT);
    }

    public List<UpcomingItemDto> upcoming() {
        return calendarService.upcoming();
    }

    // ---------------- E1 今日日报 ----------------

    @Transactional(readOnly = true)
    public DigestTodayDto today() {
        LocalDate today = LocalDate.now();
        List<House> vacant = vacantHouses();
        List<UpcomingItemDto> upcoming = upcoming();

        DigestReport report = reportRepository.findByReportDateAndDigestType(today, DIGEST_TYPE).orElse(null);
        boolean generated = report != null;
        String contentText = generated
                ? report.getContentText()
                : render(defaultTemplate().getContentText(), today, vacant, upcoming);
        long sentCount = generated
                ? notificationRecordRepository.countByReportDateAndDigestTypeAndStatusIn(
                        today, DIGEST_TYPE, List.of(
                                com.example.rentsystemjava.domain.enums.NotificationStatus.SUCCESS,
                                com.example.rentsystemjava.domain.enums.NotificationStatus.DEGRADED))
                : 0;
        String channel = generated && report.getChannel() != null
                ? report.getChannel().getValue()
                : ChannelCode.SERVERCHAN.getValue();

        return new DigestTodayDto(today.toString(),
                new DigestTodayDto.SummaryDto(vacant.size(), upcoming.size()),
                contentText, generated, channel, sentCount);
    }

    // ---------------- 链路 F：日报生成（幂等，供定时任务调用） ----------------

    /**
     * 确保当日日报已生成；返回 null 表示无事项日不生成（send_on_empty_day=0 且无空置无临期）。
     * uk_digest(report_date, digest_type) 唯一约束兜底：冲突时复用已存在正文（一天一份）。
     */
    @Transactional
    public DigestReport ensureTodayReport(LocalDate today) {
        Optional<DigestReport> existing = reportRepository.findByReportDateAndDigestType(today, DIGEST_TYPE);
        if (existing.isPresent()) {
            return existing.get();
        }
        List<House> vacant = vacantHouses();
        List<UpcomingItemDto> upcoming = upcoming();
        if (vacant.isEmpty() && upcoming.isEmpty() && !sendOnEmptyDay()) {
            return null;
        }
        DigestTemplate template = defaultTemplate();
        String content = render(template.getContentText(), today, vacant, upcoming);

        DigestReport report = new DigestReport();
        report.setReportDate(today);
        report.setDigestType(DIGEST_TYPE);
        report.setTemplateId(template.getId());
        report.setContentText(content);
        report.setVacantSnapshot(JsonUtils.toJson(vacant.stream()
                .map(h -> Map.of("id", h.getId(), "name", HouseService.addressOf(h),
                        "expectedRent", String.valueOf(h.getExpectedRent())))
                .toList()));
        report.setUpcomingSnapshot(JsonUtils.toJson(upcoming));
        report.setChannel(ChannelCode.SERVERCHAN);
        report.setIdempotencyKey(today + "|" + DIGEST_TYPE);
        report.setStatus(DigestStatus.GENERATED);
        final DigestReport saved;
        try {
            saved = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            // 并发/重试触发 uk_digest 冲突 → 复用当日已生成正文
            return reportRepository.findByReportDateAndDigestType(today, DIGEST_TYPE).orElse(null);
        }
        // 方案 B 提醒流水：写进 {临期事项清单} 的事项逐条落库
        LocalDateTime now = LocalDateTime.now();
        for (UpcomingItemDto item : upcoming) {
            ReminderType type = "pay".equals(item.type()) ? ReminderType.PAY : ReminderType.EXPIRE;
            if (reminderRecordRepository.existsByDigestIdAndHouseIdAndTypeAndPlanDate(
                    saved.getId(), item.houseId(), type, item.date())) {
                continue;
            }
            ReminderRecord rr = new ReminderRecord();
            rr.setHouseId(item.houseId());
            rr.setContractId(item.contractId());
            rr.setType(type);
            rr.setPlanDate(item.date());
            rr.setDigestId(saved.getId());
            rr.setChannel(ChannelCode.SERVERCHAN);
            rr.setSentAt(now);
            reminderRecordRepository.save(rr);
        }
        return saved;
    }

    /** tick 末尾刷新当日日报状态：全部成功→dispatched；部分失败→partial；全失败保持 generated */
    @Transactional
    public void refreshReportStatus(LocalDate today) {
        reportRepository.findByReportDateAndDigestType(today, DIGEST_TYPE).ifPresent(report -> {
            List<com.example.rentsystemjava.domain.NotificationRecord> records =
                    notificationRecordRepository.findByReportDateAndDigestType(today, DIGEST_TYPE);
            if (records.isEmpty()) {
                return;
            }
            boolean anySuccess = records.stream().anyMatch(r ->
                    r.getStatus() == NotificationStatus.SUCCESS || r.getStatus() == NotificationStatus.DEGRADED);
            boolean anyFailed = records.stream().anyMatch(r -> r.getStatus() == NotificationStatus.FAILED);
            DigestStatus target = anySuccess && anyFailed ? DigestStatus.PARTIAL
                    : anySuccess ? DigestStatus.DISPATCHED : DigestStatus.GENERATED;
            if (report.getStatus() != target) {
                report.setStatus(target);
                reportRepository.save(report);
            }
        });
    }

    private boolean sendOnEmptyDay() {
        return systemConfigRepository.findByConfigKey("send_on_empty_day")
                .map(SystemConfig::getConfigValue)
                .map("1"::equals)
                .orElse(false);
    }

    // ---------------- 模板配置（D1-D4） ----------------

    @Transactional(readOnly = true)
    public DigestTemplateDto getTemplate() {
        return toDto(defaultTemplate(), false);
    }

    /** 保存模板；空白回退默认模板并返回 restored=true（不报错） */
    @Transactional
    public DigestTemplateDto saveTemplate(String contentText) {
        DigestTemplate template = defaultTemplate();
        boolean restored;
        if (contentText == null || contentText.isBlank()) {
            template.setContentText(DEFAULT_TEMPLATE_TEXT);
            restored = true;
        } else {
            template.setContentText(contentText);
            restored = false;
        }
        templateRepository.save(template);
        return toDto(template, restored);
    }

    @Transactional(readOnly = true)
    public DigestTemplateDto resetTemplate() {
        DigestTemplate template = defaultTemplate();
        template.setContentText(DEFAULT_TEMPLATE_TEXT);
        templateRepository.save(template);
        return toDto(template, true);
    }

    /** 实时预览（不落库） */
    @Transactional(readOnly = true)
    public String preview(String contentText) {
        String text = (contentText == null || contentText.isBlank()) ? DEFAULT_TEMPLATE_TEXT : contentText;
        return render(text, LocalDate.now(), vacantHouses(), upcoming());
    }

    // ---------------- 私有 ----------------

    public DigestTemplate defaultTemplate() {
        return templateRepository.findByIsDefaultTrue()
                .orElseGet(() -> {
                    // 兜底：模板行丢失时以默认正文重建
                    DigestTemplate t = new DigestTemplate();
                    t.setName("default");
                    t.setContentText(DEFAULT_TEMPLATE_TEXT);
                    t.setIsDefault(true);
                    t.setVariablesJson(JsonUtils.toJson(List.of(
                            "{日期}", "{空置数量}", "{空置房源清单}", "{临期数量}", "{临期事项清单}")));
                    return templateRepository.save(t);
                });
    }

    private DigestTemplateDto toDto(DigestTemplate template, boolean restored) {
        return new DigestTemplateDto(template.getId(), template.getName(), template.getContentText(),
                JsonUtils.parseStringList(template.getVariablesJson()),
                Boolean.TRUE.equals(template.getIsDefault()), template.getUpdatedAt(),
                preview(template.getContentText()), restored);
    }

    private static String stripTrailingZeros(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
