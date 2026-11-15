package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.ChannelCode;
import jakarta.persistence.Converter;

@Converter
public class ChannelCodeConverter extends LowercaseEnumConverter<ChannelCode> {
    public ChannelCodeConverter() { super(ChannelCode.class); }
}
