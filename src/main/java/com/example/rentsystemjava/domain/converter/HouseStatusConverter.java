package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.HouseStatus;
import jakarta.persistence.Converter;

@Converter
public class HouseStatusConverter extends LowercaseEnumConverter<HouseStatus> {
    public HouseStatusConverter() { super(HouseStatus.class); }
}
