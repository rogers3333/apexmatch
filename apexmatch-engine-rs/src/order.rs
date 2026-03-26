//! 订单、成交、盘口等核心数据结构，字段与 Java `apexmatch-common` 对齐。

use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum OrderSide {
    Buy = 1,
    Sell = 2,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum OrderType {
    Limit,
    Market,
    StopLimit,
    StopMarket,
    Iceberg,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum TimeInForce {
    GTC,
    IOC,
    FOK,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum OrderStatus {
    New,
    PartiallyFilled,
    Filled,
    Canceled,
    Rejected,
    Expired,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Order {
    pub order_id: i64,
    pub client_order_id: Option<String>,
    pub user_id: i64,
    pub symbol: String,
    pub side: OrderSide,
    pub order_type: OrderType,
    pub time_in_force: TimeInForce,
    pub price: Option<Decimal>,
    pub quantity: Decimal,
    pub filled_quantity: Decimal,
    pub trigger_price: Option<Decimal>,
    pub take_profit_price: Option<Decimal>,
    pub stop_loss_price: Option<Decimal>,
    pub status: OrderStatus,
    pub sequence_time: i64,
    pub display_quantity: Option<Decimal>,
}

impl Order {
    pub fn remaining(&self) -> Decimal {
        self.quantity - self.filled_quantity
    }

    /// 冰山单当前切片可撮合量；普通订单等价于 remaining()。
    pub fn clip_remaining(&self) -> Decimal {
        let remaining = self.remaining();
        if self.order_type == OrderType::Iceberg {
            if let Some(display) = self.display_quantity {
                if display > Decimal::ZERO {
                    let clip_filled = self.filled_quantity % display;
                    let clip_left = display - clip_filled;
                    return clip_left.min(remaining);
                }
            }
        }
        remaining
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Trade {
    pub trade_id: i64,
    pub symbol: String,
    pub price: Decimal,
    pub quantity: Decimal,
    pub taker_order_id: i64,
    pub maker_order_id: i64,
    pub taker_user_id: i64,
    pub maker_user_id: i64,
    pub trade_time: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchResult {
    pub trades: Vec<Trade>,
    pub affected_order: Order,
    pub reject_reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DepthLevel {
    pub price: Decimal,
    pub quantity: Decimal,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MarketDepth {
    pub symbol: String,
    pub bids: Vec<DepthLevel>,
    pub asks: Vec<DepthLevel>,
    pub generated_time: i64,
}
