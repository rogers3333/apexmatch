//! 基于 [`BTreeMap`] 的高性能订单簿。
//!
//! 买盘以 [`Reverse<Decimal>`] 为键实现降序（最高优先），
//! 卖盘以 [`Decimal`] 为键实现升序（最低优先）。
//! O(1) 惰性撤单：仅从 HashMap 移除，撮合迭代时跳过已取消订单。

use std::cmp::Reverse;
use std::collections::{BTreeMap, HashMap, VecDeque};

use rust_decimal::Decimal;

use crate::order::*;

pub struct OrderBook {
    pub(crate) symbol: String,
    pub(crate) bid_levels: BTreeMap<Reverse<Decimal>, VecDeque<i64>>,
    pub(crate) ask_levels: BTreeMap<Decimal, VecDeque<i64>>,
    pub(crate) orders: HashMap<i64, Order>,
}

impl OrderBook {
    pub fn new(symbol: String) -> Self {
        Self {
            symbol,
            bid_levels: BTreeMap::new(),
            ask_levels: BTreeMap::new(),
            orders: HashMap::with_capacity(4096),
        }
    }

    pub fn symbol(&self) -> &str {
        &self.symbol
    }

    pub fn add_order(&mut self, order: Order) {
        let price = order.price.unwrap_or(Decimal::ZERO);
        let id = order.order_id;
        match order.side {
            OrderSide::Buy => {
                self.bid_levels
                    .entry(Reverse(price))
                    .or_default()
                    .push_back(id);
            }
            OrderSide::Sell => {
                self.ask_levels.entry(price).or_default().push_back(id);
            }
        }
        self.orders.insert(id, order);
    }

    /// O(1) 惰性撤单。
    pub fn cancel_order(&mut self, order_id: i64) -> Option<Order> {
        self.orders.remove(&order_id).map(|mut o| {
            o.status = OrderStatus::Canceled;
            o
        })
    }

    pub fn get_order(&self, order_id: i64) -> Option<&Order> {
        self.orders.get(&order_id)
    }

    pub fn contains(&self, order_id: i64) -> bool {
        self.orders.contains_key(&order_id)
    }

    pub fn size(&self) -> usize {
        self.orders.len()
    }

    // ==================== 盘口深度 ====================

    pub fn bid_depth(&self, levels: usize) -> Vec<DepthLevel> {
        let mut result = Vec::with_capacity(levels);
        for (Reverse(price), queue) in &self.bid_levels {
            if result.len() >= levels {
                break;
            }
            let total = self.sum_visible(queue);
            if total > Decimal::ZERO {
                result.push(DepthLevel {
                    price: *price,
                    quantity: total,
                });
            }
        }
        result
    }

    pub fn ask_depth(&self, levels: usize) -> Vec<DepthLevel> {
        let mut result = Vec::with_capacity(levels);
        for (price, queue) in &self.ask_levels {
            if result.len() >= levels {
                break;
            }
            let total = self.sum_visible(queue);
            if total > Decimal::ZERO {
                result.push(DepthLevel {
                    price: *price,
                    quantity: total,
                });
            }
        }
        result
    }

    fn sum_visible(&self, queue: &VecDeque<i64>) -> Decimal {
        let mut total = Decimal::ZERO;
        for &oid in queue {
            if let Some(order) = self.orders.get(&oid) {
                if order.status == OrderStatus::Canceled {
                    continue;
                }
                let visible = if order.order_type == OrderType::Iceberg {
                    order
                        .remaining()
                        .min(order.display_quantity.unwrap_or(order.remaining()))
                } else {
                    order.remaining()
                };
                total += visible;
            }
        }
        total
    }

    pub fn all_active_orders(&self) -> Vec<&Order> {
        self.orders.values().collect()
    }

    // ==================== 撮合核心（按价格优先/时间优先） ====================

    /// 对 taker 执行撮合，返回成交列表。taker 的 filled_quantity / status 就地更新。
    pub fn match_order(
        &mut self,
        taker: &mut Order,
        trade_id_gen: &mut impl FnMut() -> i64,
    ) -> Vec<Trade> {
        match taker.side {
            OrderSide::Buy => Self::match_against(
                &mut self.ask_levels,
                &mut self.orders,
                taker,
                trade_id_gen,
                |price, taker_price| price <= taker_price,
            ),
            OrderSide::Sell => Self::match_against_bids(
                &mut self.bid_levels,
                &mut self.orders,
                taker,
                trade_id_gen,
            ),
        }
    }

    /// 买入 taker 吃卖盘（升序遍历）
    fn match_against(
        levels: &mut BTreeMap<Decimal, VecDeque<i64>>,
        orders: &mut HashMap<i64, Order>,
        taker: &mut Order,
        gen: &mut impl FnMut() -> i64,
        price_ok: impl Fn(Decimal, Decimal) -> bool,
    ) -> Vec<Trade> {
        let mut trades = Vec::new();
        while taker.remaining() > Decimal::ZERO {
            let best = match levels.keys().next().copied() {
                Some(p) => p,
                None => break,
            };
            if taker.order_type != OrderType::Market {
                if let Some(limit) = taker.price {
                    if !price_ok(best, limit) {
                        break;
                    }
                }
            }
            Self::match_at_level(
                levels.get_mut(&best).unwrap(),
                orders,
                taker,
                best,
                gen,
                &mut trades,
            );
            if levels.get(&best).map_or(true, |q| q.is_empty()) {
                levels.remove(&best);
            }
        }
        Self::update_taker_status(taker);
        trades
    }

    /// 卖出 taker 吃买盘（降序遍历，Reverse 键）
    fn match_against_bids(
        levels: &mut BTreeMap<Reverse<Decimal>, VecDeque<i64>>,
        orders: &mut HashMap<i64, Order>,
        taker: &mut Order,
        gen: &mut impl FnMut() -> i64,
    ) -> Vec<Trade> {
        let mut trades = Vec::new();
        while taker.remaining() > Decimal::ZERO {
            let Reverse(best) = match levels.keys().next().copied() {
                Some(r) => r,
                None => break,
            };
            if taker.order_type != OrderType::Market {
                if let Some(limit) = taker.price {
                    if best < limit {
                        break;
                    }
                }
            }
            let key = Reverse(best);
            Self::match_at_level(
                levels.get_mut(&key).unwrap(),
                orders,
                taker,
                best,
                gen,
                &mut trades,
            );
            if levels.get(&key).map_or(true, |q| q.is_empty()) {
                levels.remove(&key);
            }
        }
        Self::update_taker_status(taker);
        trades
    }

    /// 在单个价格档位内按时间优先撮合（支持冰山切片重排队）。
    fn match_at_level(
        queue: &mut VecDeque<i64>,
        orders: &mut HashMap<i64, Order>,
        taker: &mut Order,
        price: Decimal,
        gen: &mut impl FnMut() -> i64,
        trades: &mut Vec<Trade>,
    ) {
        while taker.remaining() > Decimal::ZERO {
            let maker_id = match queue.front().copied() {
                Some(id) => id,
                None => break,
            };

            let maker = match orders.get_mut(&maker_id) {
                Some(m) if m.status != OrderStatus::Canceled => m,
                _ => {
                    queue.pop_front();
                    orders.remove(&maker_id);
                    continue;
                }
            };

            let matchable = maker.clip_remaining();
            if matchable <= Decimal::ZERO {
                queue.pop_front();
                continue;
            }

            let match_qty = taker.remaining().min(matchable);
            trades.push(Trade {
                trade_id: gen(),
                symbol: taker.symbol.clone(),
                price,
                quantity: match_qty,
                taker_order_id: taker.order_id,
                maker_order_id: maker.order_id,
                taker_user_id: taker.user_id,
                maker_user_id: maker.user_id,
                trade_time: crate::current_millis(),
            });

            maker.filled_quantity += match_qty;
            taker.filled_quantity += match_qty;

            if maker.remaining() <= Decimal::ZERO {
                maker.status = OrderStatus::Filled;
                queue.pop_front();
                orders.remove(&maker_id);
            } else if maker.order_type == OrderType::Iceberg
                && maker.clip_remaining() <= Decimal::ZERO
            {
                maker.status = OrderStatus::PartiallyFilled;
                queue.pop_front();
                queue.push_back(maker_id);
            } else {
                maker.status = OrderStatus::PartiallyFilled;
            }
        }
    }

    fn update_taker_status(taker: &mut Order) {
        if taker.remaining() <= Decimal::ZERO {
            taker.status = OrderStatus::Filled;
        } else if taker.filled_quantity > Decimal::ZERO {
            taker.status = OrderStatus::PartiallyFilled;
        }
    }

    /// FOK 预检：对手盘可用量是否足够完全成交。
    pub fn can_fill_completely(&self, taker: &Order) -> bool {
        let mut needed = taker.remaining();
        match taker.side {
            OrderSide::Buy => {
                for (&price, queue) in &self.ask_levels {
                    if taker.order_type != OrderType::Market {
                        if let Some(limit) = taker.price {
                            if price > limit {
                                break;
                            }
                        }
                    }
                    for &oid in queue {
                        if let Some(o) = self.orders.get(&oid) {
                            if o.status == OrderStatus::Canceled {
                                continue;
                            }
                            needed -= o.clip_remaining();
                            if needed <= Decimal::ZERO {
                                return true;
                            }
                        }
                    }
                }
            }
            OrderSide::Sell => {
                for (Reverse(price), queue) in &self.bid_levels {
                    if taker.order_type != OrderType::Market {
                        if let Some(limit) = taker.price {
                            if *price < limit {
                                break;
                            }
                        }
                    }
                    for &oid in queue {
                        if let Some(o) = self.orders.get(&oid) {
                            if o.status == OrderStatus::Canceled {
                                continue;
                            }
                            needed -= o.clip_remaining();
                            if needed <= Decimal::ZERO {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        false
    }
}
