package com.example.rentsystemjava.repository;

import com.example.rentsystemjava.domain.NotificationRecord;
import com.example.rentsystemjava.domain.enums.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    Optional<NotificationRecord> findByIdempotencyKey(String idempotencyKey);

    /** 幂等判定：同键是否已成功/降级成功（已发过不再发） */
    boolean existsByIdempotencyKeyAndStatusIn(String idempotencyKey, List<NotificationStatus> statuses);

    /** 通知记录列表（按创建时间倒序，limit 由 Pageable 控制） */
    List<NotificationRecord> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** 当日成功发送的成员数（日报卡 sentMemberCount） */
    long countByReportDateAndDigestTypeAndStatusIn(LocalDate reportDate, String digestType, List<NotificationStatus> statuses);

    /** tick 末尾刷新日报状态时，取当日全部通知记录 */
    List<NotificationRecord> findByReportDateAndDigestType(LocalDate reportDate, String digestType);
}
