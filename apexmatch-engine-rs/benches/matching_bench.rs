//! Criterion 基准测试：验证单交易对 TPS ≥ 150,000。

use criterion::{criterion_group, criterion_main, Criterion, Throughput};
use rust_decimal::Decimal;
use rust_decimal::dec;

use apexmatch_engine_rs::engine::MatchingEngine;
use apexmatch_engine_rs::order::*;

const SYM: &str = "BTC-USDT";

fn make_order(id: i64, side: OrderSide, price: Decimal, qty: Decimal) -> Order {
    Order {
        order_id: id,
        client_order_id: None,
        user_id: if side == OrderSide::Buy { 1 } else { 2 },
        symbol: SYM.into(),
        side,
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

fn bench_limit_match(c: &mut Criterion) {
    let mut group = c.benchmark_group("limit_order");
    group.throughput(Throughput::Elements(1));

    group.bench_function("buy_sell_match", |b| {
        let mut engine = MatchingEngine::new();
        engine.init(SYM.into());
        let mut id = 0i64;
        b.iter(|| {
            id += 2;
            engine.submit_order(make_order(id - 1, OrderSide::Sell, dec!(100), dec!(1)));
            engine.submit_order(make_order(id, OrderSide::Buy, dec!(100), dec!(1)));
        });
    });

    group.bench_function("no_match_insert", |b| {
        let mut engine = MatchingEngine::new();
        engine.init(SYM.into());
        let mut id = 0i64;
        b.iter(|| {
            id += 1;
            let price = Decimal::new(10000 + (id % 500), 0);
            engine.submit_order(make_order(id, OrderSide::Buy, price, dec!(1)));
        });
    });

    group.finish();
}

fn bench_cancel(c: &mut Criterion) {
    let mut group = c.benchmark_group("cancel_order");
    group.throughput(Throughput::Elements(1));

    group.bench_function("cancel_existing", |b| {
        let mut engine = MatchingEngine::new();
        engine.init(SYM.into());
        for i in 1..=10_000i64 {
            let price = Decimal::new(10000 + (i % 200), 0);
            engine.submit_order(make_order(i, OrderSide::Sell, price, dec!(1)));
        }
        let mut cancel_id = 1i64;
        b.iter(|| {
            engine.cancel_order(SYM, cancel_id);
            cancel_id += 1;
        });
    });

    group.finish();
}

criterion_group!(benches, bench_limit_match, bench_cancel);
criterion_main!(benches);
