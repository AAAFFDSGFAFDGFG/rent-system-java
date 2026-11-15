package com.example.rentsystemjava.init;

import com.example.rentsystemjava.config.RentSystemProperties;
import com.example.rentsystemjava.config.RentSystemProperties.MemberSeed;
import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.repository.MemberRepository;
import com.example.rentsystemjava.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 启动时成员初始化（CommandLineRunner）：
 * - member 表无该用户名 → AES-GCM 加密 + HMAC 哈希 + 尾四位，自动建号（默认 push_enabled=1、推送时间 09:00）
 * - 已存在但 send_key_hash 仍是占位串（__REPLACE 前缀，来自 seed-data.sql）→ 视为未初始化，补写密文与哈希
 * - 已存在且已初始化 → 跳过不覆盖（保护界面上改过的开关/推送时间）
 * 明文 SendKey 只在本方法内存中短暂存在，不落库、不写日志。
 */
@Component
public class MemberInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MemberInitializer.class);
    private static final String PLACEHOLDER_PREFIX = "__REPLACE";

    private final RentSystemProperties props;
    private final MemberRepository memberRepository;
    private final CryptoService cryptoService;

    public MemberInitializer(RentSystemProperties props, MemberRepository memberRepository, CryptoService cryptoService) {
        this.props = props;
        this.memberRepository = memberRepository;
        this.cryptoService = cryptoService;
    }

    @Override
    public void run(String... args) {
        int created = 0;
        int refreshed = 0;
        int skipped = 0;

        List<MemberSeed> seeds = new java.util.ArrayList<>();
        if (props.getInit().getMembers() != null) {
            seeds.addAll(props.getInit().getMembers());
        }
        if (props.getInit().getExtraMembers() != null) {
            seeds.addAll(props.getInit().getExtraMembers());
        }

        for (MemberSeed seed : seeds) {
            if (seed.getUsername() == null || seed.getUsername().isBlank()
                    || seed.getSendKey() == null || seed.getSendKey().isBlank()) {
                continue;
            }
            String username = seed.getUsername().trim();
            Optional<Member> existing = memberRepository.findByUsername(username);
            if (existing.isEmpty()) {
                Member member = new Member();
                member.setUsername(username);
                applySeed(member, seed);
                member.setPushEnabled(true);
                member.setDigestSendTime(parseSendTime(seed.getDigestSendTime()));
                memberRepository.save(member);
                created++;
            } else {
                Member member = existing.get();
                if (member.getSendKeyHash() != null && member.getSendKeyHash().startsWith(PLACEHOLDER_PREFIX)) {
                    applySeed(member, seed);
                    memberRepository.save(member);
                    refreshed++;
                } else {
                    skipped++;
                }
            }
        }
        log.info("成员初始化完成：新建 {} 个、刷新占位 {} 个、跳过已初始化 {} 个", created, refreshed, skipped);
    }

    private void applySeed(Member member, MemberSeed seed) {
        member.setSendKeyCipher(cryptoService.encrypt(seed.getSendKey()));
        member.setSendKeyHash(cryptoService.hmacHex(seed.getSendKey()));
        member.setSendKeyTail(tail4(seed.getSendKey()));
        if (seed.getPhone() != null && !seed.getPhone().isBlank()) {
            member.setPhone(seed.getPhone());
        }
        if (member.getDigestSendTime() == null) {
            member.setDigestSendTime(parseSendTime(seed.getDigestSendTime()));
        }
    }

    private String tail4(String key) {
        return key.length() <= 4 ? key : key.substring(key.length() - 4);
    }

    private LocalTime parseSendTime(String s) {
        try {
            return LocalTime.parse(s);
        } catch (Exception e) {
            return LocalTime.of(9, 0);
        }
    }
}
