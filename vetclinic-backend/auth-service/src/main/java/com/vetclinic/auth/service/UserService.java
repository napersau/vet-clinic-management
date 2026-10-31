package com.vetclinic.auth.service;

import com.vetclinic.auth.dto.request.RegisterRequest;
import com.vetclinic.auth.dto.response.UserResponse;

public interface UserService {

    /** Hồ sơ của người dùng đang đăng nhập; tài khoản đã bị khóa / ngừng sau khi cấp token thì bị từ chối. */
    UserResponse getMe();

    /** Chủ nuôi tự đăng ký (public): luôn là PET_OWNER, ACTIVE, tên đăng nhập là số điện thoại. */
    UserResponse register(RegisterRequest request);
}
