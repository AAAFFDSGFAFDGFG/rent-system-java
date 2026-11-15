package com.example.rentsystemjava.scheduler;

import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.service.DigestService;
import com.example.rentsystemjava.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时调度判定逻辑单测：仅验证「成员推送时间是否已到」「已成功是否跳过」「无事项日是否跳过」三处分支。
 * tick 的真实推送通过 mock 的 Dispatch/Service 交互断言，不触发外网。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DigestSchedulerTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private DigestService digestService;
    @Mock
    private NotificationDispatchService dispatchService;
    @InjectMocks
    private DigestScheduler scheduler;

    private static Member member(Long id, LocalTime sendTime, boolean enabled) {
        Member m = new Member();
        m.setId(id);
        m.setUsername("u" + id);
        m.setPushEnabled(enabled);
        m.setDigestSendTime(sendTime);
        return m;
    }

    @Test
    void 未到该成员推送时间则跳过() {
        LocalDate today = LocalDate.now();
        Member m = member(1L, LocalTime.of(23, 0), true); // 远未到
        when(memberRepository.findAllByPushEnabled(true)).thenReturn(List.of(m));
        when(dispatchService.hasSucceededToday(eq(today), eq(1L))).thenReturn(false);

        scheduler.tick();

        verify(dispatchService, never()).dispatch(any(), any(), any());
        verify(digestService, never()).ensureTodayReport(any());
    }

    @Test
    void 已到时间且未发送则生成日报并分发() {
        LocalDate today = LocalDate.now();
        Member m = member(2L, LocalTime.of(0, 0), true); // 已过
        when(memberRepository.findAllByPushEnabled(true)).thenReturn(List.of(m));
        when(dispatchService.hasSucceededToday(eq(today), eq(2L))).thenReturn(false);
        when(digestService.ensureTodayReport(today)).thenReturn(new com.example.rentsystemjava.domain.DigestReport());

        scheduler.tick();

        verify(dispatchService, times(1)).dispatch(eq(m), any(), eq(today));
        verify(digestService, times(1)).refreshReportStatus(today);
    }

    @Test
    void 当日已成功发送则跳过分发() {
        LocalDate today = LocalDate.now();
        Member m = member(3L, LocalTime.of(0, 0), true);
        when(memberRepository.findAllByPushEnabled(true)).thenReturn(List.of(m));
        when(dispatchService.hasSucceededToday(eq(today), eq(3L))).thenReturn(true);

        scheduler.tick();

        verify(dispatchService, never()).dispatch(any(), any(), any());
    }

    @Test
    void 无事项日不生成日报也不分发() {
        LocalDate today = LocalDate.now();
        Member m = member(4L, LocalTime.of(0, 0), true);
        when(memberRepository.findAllByPushEnabled(true)).thenReturn(List.of(m));
        when(dispatchService.hasSucceededToday(eq(today), eq(4L))).thenReturn(false);
        when(digestService.ensureTodayReport(today)).thenReturn(null); // 无事项日

        scheduler.tick();

        verify(dispatchService, never()).dispatch(any(), any(), any());
    }
}
