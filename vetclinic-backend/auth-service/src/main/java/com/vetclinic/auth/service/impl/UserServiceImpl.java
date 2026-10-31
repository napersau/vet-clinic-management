package com.vetclinic.auth.service.impl;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.auth.dto.request.ChangePasswordRequest;
import com.vetclinic.auth.dto.request.CreateUserRequest;
import com.vetclinic.auth.dto.request.RegisterRequest;
import com.vetclinic.auth.dto.request.UpdateProfileRequest;
import com.vetclinic.auth.dto.request.UpdateUserRequest;
import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.dto.response.UserSummaryResponse;
import com.vetclinic.auth.entity.Role;
import com.vetclinic.auth.entity.User;
import com.vetclinic.auth.mapper.UserMapper;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.repository.UserSpecifications;
import com.vetclinic.auth.service.AccountRules;
import com.vetclinic.auth.service.UserService;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** Chỉ các vai trò làm việc tại phòng cố định mới có số phòng. */
    private static final Set<RoleCode> ROOM_ROLES =
            EnumSet.of(RoleCode.VETERINARIAN, RoleCode.LAB_TECHNICIAN, RoleCode.IMAGING_TECHNICIAN);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BATCH_IDS = 200;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // ------------------------------------------------------------------ người dùng hiện tại

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User user = findCurrentUser();
        // Access Token còn hạn nhưng tài khoản đã bị khóa sau đó: không cho tiếp tục dùng
        AccountRules.assertUsable(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateMe(UpdateProfileRequest request) {
        User user = findCurrentUser();
        AccountRules.assertUsable(user);
        applyProfile(user, request.fullName(), request.phoneNumber(), request.email(), request.address());
        return userMapper.toResponse(saveAndFlush(user));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = findCurrentUser();
        AccountRules.assertUsable(user);

        if (AccountRules.exceedsBcryptLimit(request.currentPassword())
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        requireEncodablePassword(request.newPassword());
        if (request.currentPassword().equals(request.newPassword())) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // flushAutomatically ghi mật khẩu mới xuống DB trước khi thu hồi token
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        log.info("Người dùng userId={} đã đổi mật khẩu, thu hồi toàn bộ phiên", user.getId());
    }

    // ------------------------------------------------------------------ quản lý tài khoản

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        RoleCode actorRole = currentRole();
        // Lễ tân chỉ được tạo tài khoản chủ nuôi (UC-18); chỉ ADMIN mới tạo nhân sự
        if (actorRole == RoleCode.RECEPTIONIST && request.roleCode() != RoleCode.PET_OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        log.info("Tạo tài khoản: role={}, bởi userId={}", request.roleCode(), SecurityUtils.getCurrentUserId());
        return userMapper.toResponse(persistNewUser(request));
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = persistNewUser(new CreateUserRequest(null, request.password(), request.fullName(), request.phoneNumber(),
                request.email(), request.address(), null, RoleCode.PET_OWNER));
        log.info("Chủ nuôi tự đăng ký: userId={}", user.getId());
        return userMapper.toResponse(user);
    }

    /** Dùng chung cho tạo bởi nhân viên và tự đăng ký; việc kiểm tra quyền người gọi nằm ở nơi gọi. */
    private User persistNewUser(CreateUserRequest request) {
        requireEncodablePassword(request.password());

        String phone = request.phoneNumber().trim();
        String username = hasText(request.username()) ? request.username().trim() : phone;
        // Kiểm tra SĐT trước: khi tên đăng nhập mặc định là SĐT thì "trùng SĐT" là thông báo đúng với người dùng
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new AppException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
        }
        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        String room = normalizeRoom(request.roomNumber(), request.roleCode());
        if (room != null && userRepository.existsByRoomNumber(room)) {
            throw new AppException(ErrorCode.ROOM_NUMBER_ALREADY_ASSIGNED);
        }

        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        User user = User.builder()
                .role(role)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phoneNumber(phone)
                .email(blankToNull(request.email()))
                .address(blankToNull(request.address()))
                .roomNumber(room)
                .status(UserStatus.ACTIVE)
                .build();
        return saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(RoleCode role, UserStatus status, String keyword, int page, int size) {
        // Lễ tân chỉ tra cứu được khách hàng (FR-08), không xem danh sách nhân sự
        if (currentRole() == RoleCode.RECEPTIONIST) {
            role = RoleCode.PET_OWNER;
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<User> result = userRepository.findAll(UserSpecifications.filter(role, status, keyword), pageable);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findById(id));
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findById(id);
        applyProfile(user, request.fullName(), request.phoneNumber(), request.email(), request.address());

        if (request.roomNumber() != null) {
            String room = normalizeRoom(request.roomNumber(), user.getRole().getCode());
            if (room != null && userRepository.existsByRoomNumberAndIdNot(room, user.getId())) {
                throw new AppException(ErrorCode.ROOM_NUMBER_ALREADY_ASSIGNED);
            }
            user.setRoomNumber(room);
        }
        return userMapper.toResponse(saveAndFlush(user));
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long id, RoleCode newRole) {
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
        User user = findById(id);
        RoleCode oldRole = user.getRole().getCode();
        if (oldRole == RoleCode.PET_OWNER || newRole == RoleCode.PET_OWNER) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Không đổi vai trò giữa chủ nuôi và nhân sự");
        }
        if (oldRole == newRole) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Tài khoản đã có vai trò này");
        }
        user.setRole(roleRepository.findByCode(newRole).orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND)));
        if (!ROOM_ROLES.contains(newRole)) {
            user.setRoomNumber(null);   // vai trò mới không có phòng cố định
        }
        saveAndFlush(user);
        // Access Token cũ vẫn mang vai trò cũ tối đa 30 phút; Refresh Token bị thu hồi nên phiên kế tiếp phải đăng nhập lại
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        log.info("Đổi vai trò userId={}: {} -> {} bởi userId={}", id, oldRole, newRole, SecurityUtils.getCurrentUserId());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        RoleCode actorRole = currentRole();
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
        requireEncodablePassword(newPassword);
        User user = findById(id);
        // Lễ tân chỉ đặt lại mật khẩu cho chủ nuôi tại quầy, không đụng tới tài khoản nhân sự
        if (actorRole == RoleCode.RECEPTIONIST && user.getRole().getCode() != RoleCode.PET_OWNER) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        log.info("Đặt lại mật khẩu userId={} bởi userId={}", id, SecurityUtils.getCurrentUserId());
    }

    @Override
    @Transactional
    public UserResponse changeStatus(Long id, UserStatus status) {
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
        User user = findById(id);
        user.setStatus(status);
        userRepository.save(user);
        if (status != UserStatus.ACTIVE) {
            // flushAutomatically ghi trạng thái mới trước; Access Token còn lại tự hết hạn trong tối đa 30 phút
            refreshTokenRepository.revokeAllUserTokens(user.getId());
        }
        log.info("Đổi trạng thái tài khoản userId={} -> {} bởi userId={}", id, status, SecurityUtils.getCurrentUserId());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getActiveDoctors() {
        return userRepository.findByRoleCodeAndStatusOrderByFullNameAsc(RoleCode.VETERINARIAN, UserStatus.ACTIVE)
                .stream().map(userMapper::toSummary).toList();
    }

    // ------------------------------------------------------------------ nội bộ

    @Override
    @Transactional(readOnly = true)
    public UserSummaryResponse getSummary(Long id) {
        return userMapper.toSummary(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getSummaries(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH_IDS) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Tối đa " + MAX_BATCH_IDS + " id mỗi lần tra cứu");
        }
        return userRepository.findByIdIn(ids).stream().map(userMapper::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveDoctors() {
        return userRepository.countByRoleCodeAndStatus(RoleCode.VETERINARIAN, UserStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ helper

    private User findById(Long id) {
        return userRepository.findWithRoleById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private User findCurrentUser() {
        return findById(SecurityUtils.getCurrentUserId());
    }

    private RoleCode currentRole() {
        return SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    /** Cập nhật các trường hồ sơ có gửi lên (null = giữ nguyên) và kiểm tra trùng SĐT. */
    private void applyProfile(User user, String fullName, String phoneNumber, String email, String address) {
        if (fullName != null) {
            if (fullName.isBlank()) {
                throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Họ tên không được để trống");
            }
            user.setFullName(fullName.trim());
        }
        if (phoneNumber != null) {
            String phone = phoneNumber.trim();
            if (!phone.equals(user.getPhoneNumber())) {
                if (userRepository.existsByPhoneNumberAndIdNot(phone, user.getId())) {
                    throw new AppException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
                }
                user.setPhoneNumber(phone);
            }
        }
        if (email != null) {
            user.setEmail(blankToNull(email));
        }
        if (address != null) {
            user.setAddress(blankToNull(address));
        }
    }

    /** Trả về số phòng viết hoa, hoặc null nếu để trống. Chỉ Bác sĩ / KTV mới được có số phòng. */
    private static String normalizeRoom(String roomNumber, RoleCode role) {
        if (!hasText(roomNumber)) {
            return null;
        }
        if (!ROOM_ROLES.contains(role)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Chỉ Bác sĩ / Kỹ thuật viên mới có số phòng làm việc");
        }
        return roomNumber.trim().toUpperCase(Locale.ROOT);
    }

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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
