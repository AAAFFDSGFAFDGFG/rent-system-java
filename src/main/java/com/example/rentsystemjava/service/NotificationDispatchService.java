package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.config.RentSystemProperties;
import com.example.rentsystemjava.domain.DigestReport;
import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.domain.NotificationRecord;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import com.example.rentsystemjava.domain.enums.NotificationStatus;
import com.example.rentsystemjava.notification.NotificationChannel;
import com.example.rentsystemjava.notification.ServerchanChannel;
import com.example.rentsystemjava.notification.WecomChannel;
import com.example.rentsystemjava.repository.DigestReportRepository;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.repository.NotificationRecordRepository;
import com.example.rentsystemjava.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 推送分发（链路 F 编排 + F5 补发）：
 * - 默认 Server酱；失败重试 1 次；仍失败则降级企业微信（未配置则记 failed 待补发）
 * - 通知记录写入幂等键（uk_notification_idem）冲突则跳过，保证不重发
 * - 失败信息脱敏：禁止包含 SendKey（Server酱 URL 含 Key，sanitize 替换为 ***）
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final ServerchanChannel serverchan;
    private final WecomChannel wecom;
    private final CryptoService cryptoService;
    private final NotificationRecordRepository recordRepository;
    private final MemberRepository memberRepository;
    private final DigestReportRepository reportRepository;
    private final RentSystemProperties props;

    public NotificationDispatchService(ServerchanChannel serverchan, WecomChannel wecom,
                                      CryptoService cryptoService, NotificationRecordRepository recordRepository,
                                      MemberRepository memberRepository, DigestReportRepository reportRepository,
                                      RentSystemProperties props) {
        this.serverchan = serverchan;
        this.wecom = wecom;
        this.cryptoService = cryptoService;
        this.recordRepository = recordRepository;
        this.memberRepository = memberRepository;
        this.reportRepository = reportRepository;
        this.props = props;
    }

    /** 链路 F：对单个成员分发当日日报（含幂等记录写入 + 通道重试/降级） */
    @Transactional
    public void dispatch(Member member, DigestReport report, java.time.LocalDate today) {
        String idemKey = idemKey(today, member.getId());
        NotificationRecord record = new NotificationRecord();
        record.setDigestId(report.getId());
        record.setMemberId(member.getId());
        record.setReportDate(today);
        record.setDigestType(DigestService.DIGEST_TYPE);
        record.setChannel(ChannelCode.SERVERCHAN);
        record.setStatus(NotificationStatus.PENDING);
        record.setRetryCount(0);
        record.setIdempotencyKey(idemKey);
        try {
            record = recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 幂等键冲突：本 tick 已为该成员写过记录，跳过
            log.info("通知记录幂等键冲突，跳过 member={} date={}", member.getId(), today);
            return;
        }
        sendAndUpdate(record, report.getContentText(), false);
    }

    /** F5 补发：重新推送并更新记录（retry_count +1） */
    @Transactional
    public NotificationRecord resend(NotificationRecord record) {
        Member member = memberRepository.findById(record.getMemberId()).orElse(null);
        if (member == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "所属成员不存在");
        }
        DigestReport report = reportRepository.findById(record.getDigestId()).orElse(null);
        String content = report == null ? "（日报正文缺失）" : report.getContentText();
        return sendAndUpdate(record, content, true);
    }

    private NotificationRecord sendAndUpdate(NotificationRecord record, String content, boolean isResend) {
        Member member = memberRepository.findById(record.getMemberId()).orElseThrow(
                () -> new BusinessException(ErrorCode.RECORD_NOT_FOUND, "所属成员不存在"));
        String sendKey = cryptoService.decrypt(member.getSendKeyCipher());
        String title = "家庭房源日报 " + record.getReportDate();

        // 默认通道：尝试 + 重试 1 次
        NotificationChannel.ChannelResult r = serverchan.send(sendKey, title, content);
        int attempts = 1;
        if (!r.success()) {
            r = serverchan.send(sendKey, title, content);
            attempts = 2;
        }

        if (r.success()) {
            record.setStatus(NotificationStatus.SUCCESS);
            record.setChannel(ChannelCode.SERVERCHAN);
            record.setSentAt(LocalDateTime.now());
        } else {
            // 降级企业微信机器人
            String webhook = props.getNotify().getWecomWebhook();
            NotificationChannel.ChannelResult wr = wecom.send(webhook, title, content);
            if (wr.success()) {
                record.setStatus(NotificationStatus.DEGRADED);
                record.setChannel(ChannelCode.WECOM);
                record.setSentAt(LocalDateTime.now());
                record.setErrorMessage(ServerchanChannel.sanitize(r.message(), sendKey));
            } else {
                record.setStatus(NotificationStatus.FAILED);
                String msg = r.message();
                if (wr.message() != null && !"企业微信机器人未配置".equals(wr.message())) {
                    msg += "；降级: " + wr.message();
                }
                record.setErrorMessage(ServerchanChannel.sanitize(truncate(msg), sendKey));
            }
        }
        record.setRetryCount(record.getRetryCount() + (attempts - 1));
        if (isResend) {
            record.setRetryCount(record.getRetryCount() + 1);
        }
        return recordRepository.save(record);
    }

    static String idemKey(java.time.LocalDate date, Long memberId) {
        return date + "|" + memberId + "|" + DigestService.DIGEST_TYPE;
    }

    /** 当日该成员是否已成功/降级发送（调度幂等跳过判定） */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean hasSucceededToday(java.time.LocalDate today, Long memberId) {
        return recordRepository.existsByIdempotencyKeyAndStatusIn(idemKey(today, memberId),
                List.of(NotificationStatus.SUCCESS, NotificationStatus.DEGRADED));
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}
