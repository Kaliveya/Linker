package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.domain.dto.LoginDTO;
import com.sean.linker.domain.dto.RegisterDTO;
import com.sean.linker.domain.entity.LinkerUserEntity;
import com.sean.linker.domain.vo.UserVO;
import com.sean.linker.mapper.LinkerUserMapper;
import com.sean.linker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final LinkerUserMapper linkerUserMapper;

    @Override
    public Long register(RegisterDTO dto) {
        if (!StringUtils.hasText(dto.getName())) throw new IllegalArgumentException("姓名必填");
        if (!StringUtils.hasText(dto.getEmail())) throw new IllegalArgumentException("邮箱必填");
        if (!StringUtils.hasText(dto.getPassword())) throw new IllegalArgumentException("密码必填");

        LinkerUserEntity exists = linkerUserMapper.selectOne(
                new LambdaQueryWrapper<LinkerUserEntity>()
                        .eq(LinkerUserEntity::getEmail, dto.getEmail()));
        if (exists != null) throw new IllegalArgumentException("邮箱已注册: " + dto.getEmail());

        LinkerUserEntity entity = LinkerUserEntity.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .passwordHash(sha256(dto.getPassword()))
                .createdAt(LocalDateTime.now())
                .build();
        linkerUserMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public UserVO login(LoginDTO dto) {
        if (!StringUtils.hasText(dto.getEmail()) || !StringUtils.hasText(dto.getPassword())) {
            throw new IllegalArgumentException("邮箱和密码必填");
        }

        LinkerUserEntity user = linkerUserMapper.selectOne(
                new LambdaQueryWrapper<LinkerUserEntity>()
                        .eq(LinkerUserEntity::getEmail, dto.getEmail()));
        if (user == null) throw new IllegalArgumentException("邮箱或密码错误");

        if (!user.getPasswordHash().equals(sha256(dto.getPassword()))) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }

        return UserVO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    /** Demo 阶段简化：SHA-256 单向 hash，不加盐 */
    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
