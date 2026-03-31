
    /**
     * 账户信息（用于对账）
     */
    @lombok.Data
    public static class AccountInfo {
        private Long userId;
        private BigDecimal balance;
    }

    /**
     * 流水信息（用于对账）
     */
    @lombok.Data
    public static class FlowInfo {
        private Long userId;
        private BigDecimal amount;
    }
}
