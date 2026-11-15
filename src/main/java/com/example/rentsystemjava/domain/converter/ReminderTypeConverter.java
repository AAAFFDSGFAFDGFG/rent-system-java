package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.ReminderType;
import jakarta.persistence.Converter;

@Converter
public class ReminderTypeConverter extends LowercaseEnumConverter<ReminderType> {
    public ReminderTypeConverter() { super(ReminderType.class); }
}
