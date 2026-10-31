package com.vetclinic.auth.service.impl;

import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.auth.dto.request.RegisterRequest;
import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.entity.Role;
import com.vetclinic.auth.entity.User;
import com.vetclinic.auth.mapper.UserMapperImpl;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, encoder, new UserMapperImpl());
        lenient().when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(50L);
            }
            return saved;
        });
        for (RoleCode code : RoleCode.values()) {
            lenient().when(roleRepository.findByCode(code)).thenReturn(Optional.of(role(code)));
        }
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static Role role(RoleCode code) {
        return Role.builder().id((long) code.ordinal() + 1).code(code).name(code.name()).build();
    }

    private static void loginAs(long userId, RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static void assertError(ErrorCode expected, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ================================================================== tự đăng ký

    @Test
    void register_alwaysCreatesAnActivePetOwnerWithThePhoneAsUsername() {
        UserResponse response = service.register(new RegisterRequest("Chunuoi@1", "  Nguyễn Văn A ", "0944444441", "a@example.com", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getRole().getCode()).isEqualTo(RoleCode.PET_OWNER);   // không có đường nào để tự cấp quyền khác
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getUsername()).isEqualTo("0944444441");
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(user.getRoomNumber()).isNull();
        assertThat(encoder.matches("Chunuoi@1", user.getPasswordHash())).isTrue();   // lưu BCrypt, không lưu mật khẩu thô
        assertThat(response.role()).isEqualTo(RoleCode.PET_OWNER);
    }

    @Test
    void register_refusesAPhoneNumberThatIsAlreadyRegistered() {
        when(userRepository.existsByPhoneNumber("0944444441")).thenReturn(true);

        assertError(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS,
                () -> service.register(new RegisterRequest("Chunuoi@1", "A", "0944444441", null, null)));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_refusesAPasswordBcryptWouldReject() {
        String tooLong = "Aa1" + "x".repeat(80);

        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.register(new RegisterRequest(tooLong, "A", "0944444441", null, null)));
    }

    @Test
    void register_concurrentDuplicateCaughtByUniqueConstraintIsABusinessError() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertError(ErrorCode.USERNAME_ALREADY_EXISTS,
                () -> service.register(new RegisterRequest("Chunuoi@1", "A", "0944444441", null, null)));
    }

    // ================================================================== hồ sơ hiện tại

    @Test
    void getMe_returnsTheProfileOfTheCurrentUser() {
        loginAs(7L, RoleCode.PET_OWNER);
        when(userRepository.findWithRoleById(7L)).thenReturn(Optional.of(user(7L, UserStatus.ACTIVE)));

        UserResponse me = service.getMe();

        assertThat(me.id()).isEqualTo(7L);
        assertThat(me.role()).isEqualTo(RoleCode.PET_OWNER);
    }

    @Test
    void getMe_refusesAnAccountLockedAfterTheTokenWasIssued() {
        loginAs(7L, RoleCode.PET_OWNER);
        when(userRepository.findWithRoleById(7L)).thenReturn(Optional.of(user(7L, UserStatus.LOCKED)));

        assertError(ErrorCode.ACCOUNT_LOCKED, service::getMe);
    }

    private static User user(long id, UserStatus status) {
        return User.builder().id(id).role(role(RoleCode.PET_OWNER)).username("09000000" + id).passwordHash("hash")
                .fullName("Người " + id).phoneNumber("09000000" + id).status(status).build();
    }
}
