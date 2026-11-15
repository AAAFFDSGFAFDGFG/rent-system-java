package com.example.rentsystemjava.domain.converter;

import com.example.rentsystemjava.domain.enums.ValuedEnum;
import jakarta.persistence.AttributeConverter;

/**
 * 枚举 ↔ 小写字符串转换器基类。
 * 数据库存小写英文值（与原型 JS 一致），禁止用 @Enumerated(EnumType.STRING) 写大写。
 */
public abstract class LowercaseEnumConverter<E extends Enum<E> & ValuedEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowercaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.getValue().equals(dbData)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知枚举值: " + dbData + " (" + type.getSimpleName() + ")");
    }
}
