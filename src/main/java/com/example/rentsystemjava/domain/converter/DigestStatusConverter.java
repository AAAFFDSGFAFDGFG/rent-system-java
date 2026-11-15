package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.DigestStatus;
import jakarta.persistence.Converter;

@Converter
public class DigestStatusConverter extends LowercaseEnumConverter<DigestStatus> {
    public DigestStatusConverter() { super(DigestStatus.class); }
}
