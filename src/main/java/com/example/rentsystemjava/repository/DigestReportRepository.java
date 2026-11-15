package com.example.rentsystemjava.repository;

import com.example.rentsystemjava.domain.DigestReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DigestReportRepository extends JpaRepository<DigestReport, Long> {

    Optional<DigestReport> findByReportDateAndDigestType(LocalDate reportDate, String digestType);
}
