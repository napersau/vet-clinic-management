package com.vetclinic.billing.repository;

import com.vetclinic.billing.constant.PaymentStatus;

import java.math.BigDecimal;

public record StatusRow(PaymentStatus status, Long invoiceCount, BigDecimal finalAmount) {
}
