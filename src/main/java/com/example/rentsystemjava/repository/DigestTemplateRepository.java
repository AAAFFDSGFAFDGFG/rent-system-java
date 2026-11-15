package com.example.rentsystemjava.repository;

import com.example.rentsystemjava.domain.DigestTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DigestTemplateRepository extends JpaRepository<DigestTemplate, Long> {

    Optional<DigestTemplate> findByName(String name);

    Optional<DigestTemplate> findByIsDefaultTrue();
}
