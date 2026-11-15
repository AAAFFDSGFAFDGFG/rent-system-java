package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.EndReason;
import jakarta.persistence.Converter;

@Converter
public class EndReasonConverter extends LowercaseEnumConverter<EndReason> {
    public EndReasonConverter() { super(EndReason.class); }
}
