package com.example.rentsystemjava.repository;

import com.example.rentsystemjava.domain.ReminderRecord;
import com.example.rentsystemjava.domain.enums.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ReminderRecordRepository extends JpaRepository<ReminderRecord, Long> {

    /** uk_reminder(digest_id, house_id, type, plan_date) 幂等检查 */
    boolean existsByDigestIdAndHouseIdAndTypeAndPlanDate(Long digestId, Long houseId, ReminderType type, LocalDate planDate);
}
