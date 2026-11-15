package com.example.rentsystemjava.dto;

/** 退租请求（C6）。confirmBreach 仅在后端判定提前违约且用户确认后传 true。 */
public record VacateRequest(
        Boolean confirmBreach) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirmBreach);
    }
}
