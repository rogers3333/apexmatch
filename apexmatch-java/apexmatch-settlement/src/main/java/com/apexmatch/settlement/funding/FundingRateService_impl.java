
    /**
     * 执行资金费率结算（原子化）
     * 多头持仓：资金费率为正，向空头支付费用；资金费率为负，向空头收取费用
     * 空头持仓：与多头相反
     */
    @Transactional(rollbackFor = Exception.class)
    public void settleFundingRate(String symbol, List<PositionInfo> positions) {
        FundingRate rate = currentRates.get(symbol);
        if (rate == null) {
            log.warn("资金费率不存在: symbol={}", symbol);
            return;
        }

        log.info("开始资金费率结算: symbol={}, rate={}, positions={}",
                symbol, rate.getFundingRate(), positions.size());

        List<FundingSettlement> batchSettlements = new ArrayList<>();

        for (PositionInfo position : positions) {
            BigDecimal notionalValue = position.getMarkPrice()
                    .multiply(position.getPositionQuantity())
                    .multiply(position.getContractMultiplier());

            BigDecimal fundingFee = notionalValue.multiply(rate.getFundingRate())
                    .setScale(8, RoundingMode.HALF_UP);

            if ("LONG".equals(position.getPositionSide())) {
                fundingFee = fundingFee.negate();
            }

            FundingSettlement settlement = new FundingSettlement();
            settlement.setSettlementId(System.currentTimeMillis() + position.getUserId());
            settlement.setUserId(position.getUserId());
            settlement.setSymbol(symbol);
            settlement.setPositionSide(position.getPositionSide());
            settlement.setPositionQuantity(position.getPositionQuantity());
            settlement.setMarkPrice(position.getMarkPrice());
            settlement.setNotionalValue(notionalValue);
            settlement.setFundingRate(rate.getFundingRate());
            settlement.setFundingFee(fundingFee);
            settlement.setStatus("SETTLED");
            settlement.setSettlementTime(LocalDateTime.now());
            settlement.setCreateTime(LocalDateTime.now());

            batchSettlements.add(settlement);
        }

        settlements.addAll(batchSettlements);
        log.info("资金费率结算完成: symbol={}, count={}", symbol, batchSettlements.size());
    }

    /**
     * 查询用户资金费用历史
     */
    public List<FundingSettlement> getUserSettlements(Long userId, String symbol) {
        return settlements.stream()
                .filter(s -> s.getUserId().equals(userId) && s.getSymbol().equals(symbol))
                .toList();
    }

    /**
     * 持仓信息（用于结算）
     */
    @lombok.Data
    public static class PositionInfo {
        private Long userId;
        private String positionSide;
        private BigDecimal positionQuantity;
        private BigDecimal markPrice;
        private BigDecimal contractMultiplier;
    }
}
