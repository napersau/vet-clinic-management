package com.vetclinic.auth.constant;

public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** SĐT Việt Nam 10 số, bắt đầu bằng 0 (UC-18). */
    public static final String PHONE = "^0\\d{9}$";

    /** Mật khẩu 8-72 ký tự, có ít nhất 1 chữ cái và 1 chữ số. BCrypt chỉ xử lý tối đa 72 byte. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

    /** Số phòng làm việc, ví dụ P01, LAB01, XQ01. */
    public static final String ROOM_NUMBER = "^[A-Za-z0-9_-]{1,20}$";

    public static final String PASSWORD_MESSAGE = "Mật khẩu phải dài 8-72 ký tự, gồm ít nhất 1 chữ cái và 1 chữ số";
    public static final String PHONE_MESSAGE = "Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 0";
    public static final String ROOM_MESSAGE = "Số phòng chỉ gồm chữ, số, '-' hoặc '_' (tối đa 20 ký tự)";
}
