package com.example.rentsystemjava.scheduler;

import com.example.rentsystemjava.domain.DigestReport;
import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.service.DigestService;
import com.example.rentsystemjava.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 每日聚合日报定时推送（链路 F 用户定制方案）：
 * - 每小时整点 tick；逐成员判定「现在是否 ≥ 该成员 digest_send_time」
 * - 非整点设定最多延后到下一整点发出（如 09:30 → 10:00）
 * - 未按各自时间 / 今日已成功发送的成员跳过；无事项日不生成日报也不推送
 */
@Component
public class DigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

    private final MemberRepository memberRepository;
    private final DigestService digestService;
    private final NotificationDispatchService dispatchService;

    public DigestScheduler(MemberRepository memberRepository, DigestService digestService,
                          NotificationDispatchService dispatchService) {
        this.memberRepository = memberRepository;
        this.digestService = digestService;
        this.dispatchService = dispatchService;
    }

    /** 每小时整点触发 */
    @Scheduled(cron = "0 0 * * * ?")
    public void tick() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        log.info("日报 tick 开始：检查开启推送的成员（now={}）", now);
        List<Member> members = memberRepository.findAllByPushEnabled(true);
        for (Member member : members) {
            LocalTime sendAt = member.getDigestSendTime() == null ? LocalTime.of(9, 0) : member.getDigestSendTime();
            if (now.isBefore(sendAt)) {
                continue; // 未到该成员推送时间
            }
            // 今日已成功/降级 → 跳过
            if (dispatchService.hasSucceededToday(today, member.getId())) {
                continue;
            }
            DigestReport report = digestService.ensureTodayReport(today);
            if (report == null) {
                continue; // 无事项日（send_on_empty_day=0 且空置与临期均空）
            }
            dispatchService.dispatch(member, report, today);
        }
        digestService.refreshReportStatus(today);
        log.info("日报 tick 完成");
    }
}
