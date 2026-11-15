package com.example.rentsystemjava.service;

import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.domain.NotificationRecord;
import com.example.rentsystemjava.domain.enums.NotificationStatus;
import com.example.rentsystemjava.dto.NotificationRecordDto;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.repository.NotificationRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 通知记录查询（F4）与补发（F5） */
@Service
public class NotificationQueryService {

    private final NotificationRecordRepository recordRepository;
    private final MemberRepository memberRepository;
    private final NotificationDispatchService dispatchService;

    public NotificationQueryService(NotificationRecordRepository recordRepository,
                                   MemberRepository memberRepository,
                                   NotificationDispatchService dispatchService) {
        this.recordRepository = recordRepository;
        this.memberRepository = memberRepository;
        this.dispatchService = dispatchService;
    }

    /** F4：通知记录列表（按创建时间倒序，limit 默认 20、上限 100） */
    @Transactional(readOnly = true)
    public List<NotificationRecordDto> list(Integer limit) {
        int cap = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        List<NotificationRecord> records = recordRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, cap));
        Map<Long, String> usernames = memberRepository.findAll().stream()
                .collect(Collectors.toMap(Member::getId, Member::getUsername));
        return records.stream().map(r -> new NotificationRecordDto(
                r.getId(),
                r.getReportDate(),
                r.getChannel() == null ? null : r.getChannel().getValue(),
                r.getStatus(),
                usernames.getOrDefault(r.getMemberId(), "未知"),
                r.getSentAt(),
                r.getRetryCount(),
                r.getStatus() == NotificationStatus.FAILED || r.getStatus() == NotificationStatus.DEGRADED
        )).collect(Collectors.toList());
    }

    /** F5：补发单条 */
    public NotificationRecordDto resend(Long id) {
        NotificationRecord record = recordRepository.findById(id).orElse(null);
        if (record == null) {
            throw new com.example.rentsystemjava.common.BusinessException(
                    com.example.rentsystemjava.common.ErrorCode.RECORD_NOT_FOUND, "通知记录不存在");
        }
        // 已成功/降级的记录无需补发
        if (record.getStatus() == NotificationStatus.SUCCESS || record.getStatus() == NotificationStatus.DEGRADED) {
            throw new com.example.rentsystemjava.common.BusinessException(
                    com.example.rentsystemjava.common.ErrorCode.RECORD_ALREADY_SENT, "该记录已成功发送，无需补发");
        }
        NotificationRecord updated = dispatchService.resend(record);
        return new NotificationRecordDto(
                updated.getId(),
                updated.getReportDate(),
                updated.getChannel() == null ? null : updated.getChannel().getValue(),
                updated.getStatus(),
                memberRepository.findById(updated.getMemberId()).map(Member::getUsername).orElse("未知"),
                updated.getSentAt(),
                updated.getRetryCount(),
                false);
    }
}
