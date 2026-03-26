//! ApexMatch Rust 高性能撮合引擎。
//!
//! 提供与 Java 引擎逻辑一致的撮合实现，编译为 `cdylib` 供 JNA 调用。

pub mod order;
pub mod orderbook;
pub mod engine;
pub mod wal;
pub mod ffi;

pub use order::*;
pub use orderbook::OrderBook;
pub use engine::MatchingEngine;

pub(crate) fn current_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
