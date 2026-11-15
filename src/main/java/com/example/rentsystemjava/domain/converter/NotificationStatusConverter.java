package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.NotificationStatus;
import jakarta.persistence.Converter;

@Converter
public class NotificationStatusConverter extends LowercaseEnumConverter<NotificationStatus> {
    public NotificationStatusConverter() { super(NotificationStatus.class); }
}
