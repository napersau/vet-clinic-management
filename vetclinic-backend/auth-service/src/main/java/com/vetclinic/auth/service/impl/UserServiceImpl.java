package com.vetclinic.auth.service.impl;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.auth.dto.request.RegisterRequest;
import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.entity.Role;
import com.vetclinic.auth.entity.User;
import com.vetclinic.auth.mapper.UserMapper;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.service.AccountRules;
import com.vetclinic.auth.service.UserService;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // ------------------------------------------------------------------ người dùng hiện tại

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User user = userRepository.findWithRoleById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        // Access Token còn hạn nhưng tài khoản đã bị khóa sau đó: không cho tiếp tục dùng
        AccountRules.assertUsable(user);
        return userMapper.toResponse(user);
    }

    // ------------------------------------------------------------------ tự đăng ký

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        requireEncodablePassword(request.password());

        String phone = request.phoneNumber().trim();
        // Tên đăng nhập là SĐT nên "trùng SĐT" là thông báo đúng với người dùng; kiểm tra thêm username phòng khi
        // một tài khoản nhân sự đã đặt tên đăng nhập trùng với SĐT này
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new AppException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
        }
        if (userRepository.existsByUsername(phone)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        Role role = roleRepository.findByCode(RoleCode.PET_OWNER)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        User user = saveAndFlush(User.builder()
                .role(role)
                .username(phone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phoneNumber(phone)
                .email(blankToNull(request.email()))
                .address(blankToNull(request.address()))
                .status(UserStatus.ACTIVE)
                .build());
        log.info("Chủ nuôi tự đăng ký: userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    // ------------------------------------------------------------------ helper

    private static void requireEncodablePassword(String rawPassword) {
        if (AccountRules.exceedsBcryptLimit(rawPassword)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Mật khẩu tối đa 72 byte");
        }
    }

    private User saveAndFlush(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đồng thời cùng qua bước kiểm tra trùng; ràng buộc UNIQUE của DB là chốt chặn cuối
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS, "Tên đăng nhập hoặc số điện thoại đã tồn tại");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
