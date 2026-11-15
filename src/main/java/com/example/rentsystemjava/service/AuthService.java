package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.dto.MemberProfileDto;
import com.example.rentsystemjava.dto.PushSettingsRequest;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * 认证与成员服务（链路 A）。
 * 登录 = HMAC 比对 send_key_hash（不区分用户名/SendKey 哪个错，统一 1002）；无限流（用户决策）。
 * 明文 SendKey 不落库、不写日志、不返回任何响应。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository memberRepository;
    private final CryptoService cryptoService;

    public AuthService(MemberRepository memberRepository, CryptoService cryptoService) {
        this.memberRepository = memberRepository;
        this.cryptoService = cryptoService;
    }

    /** 登录校验，成功返回成员（供控制层签发会话 Cookie） */
    public Member login(String username, String sendKey) {
        if (username == null || username.isBlank() || sendKey == null || sendKey.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        Member member = memberRepository.findByUsername(username.trim())
                .orElseThrow(() -> {
                    log.warn("登录失败：用户名不存在（不区分错误原因）");
                    return new BusinessException(ErrorCode.LOGIN_FAILED);
                });
        String computed = cryptoService.hmacHex(sendKey);
        if (!constantTimeEquals(computed, member.getSendKeyHash())) {
            log.warn("登录失败：SendKey 校验不匹配 username={}", username);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    public MemberProfileDto toProfile(Member member) {
        return new MemberProfileDto(member.getUsername(), member.getPhone(),
                member.getSendKeyTail(), member.getPushEnabled(), member.getDigestSendTime());
    }

    /** 当前登录成员（拦截器已放入 memberId） */
    public Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /** 更新本人推送设置：开关与推送时间（链路 A 第 6 步扩展，仅更新提交的字段） */
    @Transactional
    public MemberProfileDto updatePushSettings(Long memberId, PushSettingsRequest request) {
        Member member = requireMember(memberId);
        if (request.pushEnabled() != null) {
            member.setPushEnabled(request.pushEnabled());
        }
        if (request.digestSendTime() != null && !request.digestSendTime().isBlank()) {
            member.setDigestSendTime(parseSendTime(request.digestSendTime()));
        }
        memberRepository.save(member);
        return toProfile(member);
    }

    private LocalTime parseSendTime(String s) {
        try {
            return LocalTime.parse(s.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "推送时间格式须为 HH:mm（如 09:00）");
        }
    }

    /** 恒时比较，避免时序侧信道 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
