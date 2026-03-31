package com.apexmatch.agent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Referral {
    private Long referralId;
    private Long agentId;
    private Long referredUserId;
    private String referralCode;
    private LocalDateTime registeredAt;
    private LocalDateTime createdAt;
}
