package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.ContractStatus;
import jakarta.persistence.Converter;

@Converter
public class ContractStatusConverter extends LowercaseEnumConverter<ContractStatus> {
    public ContractStatusConverter() { super(ContractStatus.class); }
}
