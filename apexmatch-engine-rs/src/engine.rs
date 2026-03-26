//! 撮合引擎：管理多交易对 OrderBook + StopOrderBook，逻辑与 Java 引擎完全一致。

use std::collections::{BTreeMap, HashMap};

use rust_decimal::Decimal;

use crate::order::*;
use crate::orderbook::OrderBook;

// ==================== StopOrderBook ====================

/// 止损订单簿，按 trigger_price 索引。
pub(crate) struct StopOrderBook {
    buy_stops: BTreeMap<Decimal, Vec<i64>>,
    sell_stops: BTreeMap<Decimal, Vec<i64>>,
    orders: HashMap<i64, Order>,
}

impl StopOrderBook {
    pub fn new() -> Self {
        Self {
            buy_stops: BTreeMap::new(),
            sell_stops: BTreeMap::new(),
            orders: HashMap::new(),
        }
    }

    pub fn add(&mut self, order: Order) {
        let trigger = order.trigger_price.unwrap_or(Decimal::ZERO);
        let id = order.order_id;
        match order.side {
            OrderSide::Buy => self.buy_stops.entry(trigger).or_default().push(id),
            OrderSide::Sell => self.sell_stops.entry(trigger).or_default().push(id),
        }
        self.orders.insert(id, order);
    }

    pub fn remove(&mut self, order_id: i64) -> Option<Order> {
        self.orders.remove(&order_id).map(|mut o| {
            o.status = OrderStatus::Canceled;
            o
        })
    }

    pub fn contains(&self, order_id: i64) -> bool {
        self.orders.contains_key(&order_id)
    }

    pub fn size(&self) -> usize {
        self.orders.len()
    }

    /// 根据最新成交价触发止损单，返回已触发的订单。
    pub fn check_triggers(&mut self, last_price: Decimal) -> Vec<Order> {
        let mut triggered = Vec::new();

        let buy_keys: Vec<_> = self
            .buy_stops
            .range(..=last_price)
            .map(|(k, _)| *k)
            .collect();
        for key in buy_keys {
            if let Some(ids) = self.buy_stops.remove(&key) {
                for id in ids {
                    if let Some(o) = self.orders.remove(&id) {
                        if o.status != OrderStatus::Canceled {
                            triggered.push(o);
                        }
                    }
                }
            }
        }

        let sell_keys: Vec<_> = self
            .sell_stops
            .range(last_price..)
            .map(|(k, _)| *k)
            .collect();
        for key in sell_keys {
            if let Some(ids) = self.sell_stops.remove(&key) {
                for id in ids {
                    if let Some(o) = self.orders.remove(&id) {
                        if o.status != OrderStatus::Canceled {
                            triggered.push(o);
                        }
                    }
                }
            }
        }

        triggered
    }

    pub fn all_orders(&self) -> Vec<&Order> {
        self.orders.values().collect()
    }
}

// ==================== MatchingEngine ====================

pub struct MatchingEngine {
    books: HashMap<String, OrderBook>,
    stop_books: HashMap<String, StopOrderBook>,
    next_trade_id: i64,
}

impl MatchingEngine {
    pub fn new() -> Self {
        Self {
            books: HashMap::new(),
            stop_books: HashMap::new(),
            next_trade_id: 0,
        }
    }

    pub fn init(&mut self, symbol: String) {
        self.books
            .entry(symbol.clone())
            .or_insert_with(|| OrderBook::new(symbol.clone()));
        self.stop_books
            .entry(symbol)
            .or_insert_with(StopOrderBook::new);
    }

    pub fn submit_order(&mut self, order: Order) -> MatchResult {
        self.ensure_init(&order.symbol);

        if order.order_type == OrderType::StopLimit || order.order_type == OrderType::StopMarket {
            return self.handle_stop(order);
        }

        self.match_and_place(order)
    }

    pub fn cancel_order(&mut self, symbol: &str, order_id: i64) -> Option<Order> {
        if let Some(book) = self.books.get_mut(symbol) {
            if let Some(o) = book.cancel_order(order_id) {
                return Some(o);
            }
        }
        if let Some(sb) = self.stop_books.get_mut(symbol) {
            if let Some(o) = sb.remove(order_id) {
                return Some(o);
            }
        }
        None
    }

    pub fn get_market_depth(&self, symbol: &str, levels: usize) -> MarketDepth {
        let (bids, asks) = match self.books.get(symbol) {
            Some(b) => (b.bid_depth(levels), b.ask_depth(levels)),
            None => (vec![], vec![]),
        };
        MarketDepth {
            symbol: symbol.to_string(),
            bids,
            asks,
            generated_time: crate::current_millis(),
        }
    }

    // ==================== 内部方法 ====================

    fn match_and_place(&mut self, mut order: Order) -> MatchResult {
        let symbol = order.symbol.clone();
        let tif = order.time_in_force;

        // FOK 预检
        if tif == TimeInForce::FOK {
            let book = self.books.get(&symbol).unwrap();
            if !book.can_fill_completely(&order) {
                order.status = OrderStatus::Rejected;
                return MatchResult {
                    trades: vec![],
                    affected_order: order,
                    reject_reason: Some("FOK_CANNOT_FILL".into()),
                };
            }
        }

        // 撮合
        let mut trade_id = self.next_trade_id;
        let mut gen = || {
            trade_id += 1;
            trade_id
        };
        let book = self.books.get_mut(&symbol).unwrap();
        let mut trades = book.match_order(&mut order, &mut gen);
        self.next_trade_id = trade_id;

        let has_fill = !trades.is_empty();
        let leftover = order.remaining();

        // 处理剩余
        if leftover > Decimal::ZERO {
            match tif {
                TimeInForce::IOC => {
                    order.status = if has_fill {
                        OrderStatus::PartiallyFilled
                    } else {
                        OrderStatus::Canceled
                    };
                }
                TimeInForce::FOK => {
                    order.status = OrderStatus::Filled;
                }
                TimeInForce::GTC => {
                    if order.order_type == OrderType::Market {
                        order.status = if has_fill {
                            OrderStatus::PartiallyFilled
                        } else {
                            OrderStatus::Canceled
                        };
                    } else {
                        let book = self.books.get_mut(&symbol).unwrap();
                        book.add_order(order.clone());
                        order.status = if has_fill {
                            OrderStatus::PartiallyFilled
                        } else {
                            OrderStatus::New
                        };
                    }
                }
            }
        }

        // 止损触发
        if let Some(last_trade) = trades.last() {
            let last_price = last_trade.price;
            let stop_results = self.trigger_stops(&symbol, last_price);
            for sr in stop_results {
                trades.extend(sr.trades);
            }
        }

        MatchResult {
            trades,
            affected_order: order,
            reject_reason: None,
        }
    }

    fn handle_stop(&mut self, mut order: Order) -> MatchResult {
        let symbol = order.symbol.clone();
        order.status = OrderStatus::New;
        let out = order.clone();
        self.stop_books.get_mut(&symbol).unwrap().add(order);
        MatchResult {
            trades: vec![],
            affected_order: out,
            reject_reason: None,
        }
    }

    fn trigger_stops(&mut self, symbol: &str, last_price: Decimal) -> Vec<MatchResult> {
        let sb = match self.stop_books.get_mut(symbol) {
            Some(sb) => sb,
            None => return vec![],
        };
        let triggered = sb.check_triggers(last_price);
        let mut results = Vec::new();
        for mut order in triggered {
            match order.order_type {
                OrderType::StopMarket => order.order_type = OrderType::Market,
                OrderType::StopLimit => order.order_type = OrderType::Limit,
                _ => {}
            }
            results.push(self.match_and_place(order));
        }
        results
    }

    fn ensure_init(&mut self, symbol: &str) {
        if !self.books.contains_key(symbol) {
            self.init(symbol.to_string());
        }
    }

    #[cfg(test)]
    pub(crate) fn book(&self, symbol: &str) -> Option<&OrderBook> {
        self.books.get(symbol)
    }

    #[cfg(test)]
    pub(crate) fn stop_book(&self, symbol: &str) -> Option<&StopOrderBook> {
        self.stop_books.get(symbol)
    }
}

impl Default for MatchingEngine {
    fn default() -> Self {
        Self::new()
    }
}

// ==================== 单元测试 ====================

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal::dec;
    use std::sync::atomic::{AtomicI64, Ordering};

    static ORDER_SEQ: AtomicI64 = AtomicI64::new(1000);
    const SYM: &str = "BTC-USDT";

    fn next_id() -> i64 {
        ORDER_SEQ.fetch_add(1, Ordering::Relaxed)
    }

    fn buy_limit(price: Decimal, qty: Decimal) -> Order {
        Order {
            order_id: next_id(),
            client_order_id: None,
            user_id: 1,
            symbol: SYM.into(),
            side: OrderSide::Buy,
            order_type: OrderType::Limit,
            time_in_force: TimeInForce::GTC,
            price: Some(price),
            quantity: qty,
            filled_quantity: Decimal::ZERO,
            trigger_price: None,
            take_profit_price: None,
            stop_loss_price: None,
            status: OrderStatus::New,
            sequence_time: 0,
            display_quantity: None,
        }
    }

    fn sell_limit(price: Decimal, qty: Decimal) -> Order {
        let mut o = buy_limit(price, qty);
        o.order_id = next_id();
        o.user_id = 2;
        o.side = OrderSide::Sell;
        o
    }

    fn market_buy(qty: Decimal) -> Order {
        let mut o = buy_limit(Decimal::ZERO, qty);
        o.order_type = OrderType::Market;
        o.price = None;
        o
    }

    fn market_sell(qty: Decimal) -> Order {
        let mut o = sell_limit(Decimal::ZERO, qty);
        o.order_type = OrderType::Market;
        o.price = None;
        o
    }

    fn new_engine() -> MatchingEngine {
        let mut e = MatchingEngine::new();
        e.init(SYM.into());
        e
    }

    // ---------- Limit ----------

    #[test]
    fn exact_match() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(10)));
        let r = e.submit_order(buy_limit(dec!(100), dec!(10)));
        assert_eq!(r.trades.len(), 1);
        assert_eq!(r.affected_order.status, OrderStatus::Filled);
        assert_eq!(e.book(SYM).unwrap().size(), 0);
    }

    #[test]
    fn partial_fill() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(5)));
        let r = e.submit_order(buy_limit(dec!(100), dec!(10)));
        assert_eq!(r.trades.len(), 1);
        assert_eq!(r.trades[0].quantity, dec!(5));
        assert_eq!(r.affected_order.status, OrderStatus::PartiallyFilled);
        assert_eq!(e.book(SYM).unwrap().size(), 1);
    }

    #[test]
    fn no_match_price_gap() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(110), dec!(10)));
        let r = e.submit_order(buy_limit(dec!(100), dec!(10)));
        assert!(r.trades.is_empty());
        assert_eq!(r.affected_order.status, OrderStatus::New);
        assert_eq!(e.book(SYM).unwrap().size(), 2);
    }

    #[test]
    fn price_priority_asks() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(102), dec!(5)));
        e.submit_order(sell_limit(dec!(100), dec!(5)));
        e.submit_order(sell_limit(dec!(101), dec!(5)));
        let r = e.submit_order(buy_limit(dec!(102), dec!(10)));
        assert_eq!(r.trades.len(), 2);
        assert_eq!(r.trades[0].price, dec!(100));
        assert_eq!(r.trades[1].price, dec!(101));
    }

    #[test]
    fn price_priority_bids() {
        let mut e = new_engine();
        e.submit_order(buy_limit(dec!(98), dec!(5)));
        e.submit_order(buy_limit(dec!(100), dec!(5)));
        e.submit_order(buy_limit(dec!(99), dec!(5)));
        let r = e.submit_order(sell_limit(dec!(98), dec!(10)));
        assert_eq!(r.trades.len(), 2);
        assert_eq!(r.trades[0].price, dec!(100));
        assert_eq!(r.trades[1].price, dec!(99));
    }

    #[test]
    fn time_priority() {
        let mut e = new_engine();
        let first = sell_limit(dec!(100), dec!(5));
        let first_id = first.order_id;
        e.submit_order(first);
        e.submit_order(sell_limit(dec!(100), dec!(5)));
        let r = e.submit_order(buy_limit(dec!(100), dec!(5)));
        assert_eq!(r.trades[0].maker_order_id, first_id);
    }

    // ---------- Market ----------

    #[test]
    fn market_buy_fills() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(5)));
        e.submit_order(sell_limit(dec!(101), dec!(5)));
        let r = e.submit_order(market_buy(dec!(10)));
        assert_eq!(r.trades.len(), 2);
        assert_eq!(r.affected_order.status, OrderStatus::Filled);
    }

    #[test]
    fn market_no_liquidity() {
        let mut e = new_engine();
        let r = e.submit_order(market_buy(dec!(10)));
        assert!(r.trades.is_empty());
        assert_eq!(r.affected_order.status, OrderStatus::Canceled);
    }

    // ---------- FOK ----------

    #[test]
    fn fok_success() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(10)));
        let mut o = buy_limit(dec!(100), dec!(10));
        o.time_in_force = TimeInForce::FOK;
        let r = e.submit_order(o);
        assert_eq!(r.trades.len(), 1);
        assert_eq!(r.affected_order.status, OrderStatus::Filled);
    }

    #[test]
    fn fok_rejected() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(5)));
        let mut o = buy_limit(dec!(100), dec!(10));
        o.time_in_force = TimeInForce::FOK;
        let r = e.submit_order(o);
        assert!(r.trades.is_empty());
        assert_eq!(r.affected_order.status, OrderStatus::Rejected);
        assert_eq!(r.reject_reason.as_deref(), Some("FOK_CANNOT_FILL"));
    }

    // ---------- IOC ----------

    #[test]
    fn ioc_partial() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(3)));
        let mut o = buy_limit(dec!(100), dec!(10));
        o.time_in_force = TimeInForce::IOC;
        let r = e.submit_order(o);
        assert_eq!(r.trades.len(), 1);
        assert_eq!(r.affected_order.filled_quantity, dec!(3));
        assert_eq!(r.affected_order.status, OrderStatus::PartiallyFilled);
        assert_eq!(e.book(SYM).unwrap().size(), 0);
    }

    // ---------- Stop ----------

    #[test]
    fn stop_market_trigger() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(105), dec!(10)));
        let mut stop = market_buy(dec!(5));
        stop.order_type = OrderType::StopMarket;
        stop.trigger_price = Some(dec!(105));
        e.submit_order(stop);
        assert_eq!(e.stop_book(SYM).unwrap().size(), 1);
        e.submit_order(buy_limit(dec!(105), dec!(2)));
        assert_eq!(e.stop_book(SYM).unwrap().size(), 0);
    }

    // ---------- Cancel ----------

    #[test]
    fn cancel_order() {
        let mut e = new_engine();
        let sell = sell_limit(dec!(100), dec!(10));
        let id = sell.order_id;
        e.submit_order(sell);
        assert!(e.cancel_order(SYM, id).is_some());
        assert_eq!(e.book(SYM).unwrap().size(), 0);
    }

    #[test]
    fn cancel_nonexistent() {
        let mut e = new_engine();
        assert!(e.cancel_order(SYM, 99999).is_none());
    }

    // ---------- Iceberg ----------

    #[test]
    fn iceberg_depth() {
        let mut e = new_engine();
        let mut ice = sell_limit(dec!(100), dec!(50));
        ice.order_type = OrderType::Iceberg;
        ice.display_quantity = Some(dec!(10));
        e.submit_order(ice);
        let d = e.get_market_depth(SYM, 10);
        assert_eq!(d.asks.len(), 1);
        assert_eq!(d.asks[0].quantity, dec!(10));
    }

    #[test]
    fn iceberg_full_consume() {
        let mut e = new_engine();
        let mut ice = sell_limit(dec!(100), dec!(20));
        ice.order_type = OrderType::Iceberg;
        ice.display_quantity = Some(dec!(10));
        e.submit_order(ice);
        let r = e.submit_order(buy_limit(dec!(100), dec!(20)));
        assert_eq!(r.affected_order.status, OrderStatus::Filled);
        assert_eq!(e.book(SYM).unwrap().size(), 0);
    }

    // ---------- Depth ----------

    #[test]
    fn depth_aggregation() {
        let mut e = new_engine();
        e.submit_order(sell_limit(dec!(100), dec!(5)));
        e.submit_order(sell_limit(dec!(100), dec!(3)));
        e.submit_order(sell_limit(dec!(101), dec!(7)));
        let d = e.get_market_depth(SYM, 10);
        assert_eq!(d.asks.len(), 2);
        assert_eq!(d.asks[0].quantity, dec!(8));
    }
}
