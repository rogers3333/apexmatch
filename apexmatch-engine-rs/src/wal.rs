//! 简易 WAL（Write-Ahead Log）。
//!
//! 每条记录：`[4 字节长度][bincode 编码的 WalEntry]`。
//! 开发阶段使用 `BufWriter` + `flush`；生产可替换为 `O_DIRECT` / mmap。

use std::fs::{self, File, OpenOptions};
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::order::Order;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum WalEntry {
    Submit(Order),
    Cancel { symbol: String, order_id: i64 },
}

pub struct WalManager {
    path: PathBuf,
    writer: BufWriter<File>,
}

impl WalManager {
    pub fn open(dir: &Path, symbol: &str) -> std::io::Result<Self> {
        fs::create_dir_all(dir)?;
        let path = dir.join(format!("{symbol}.wal"));
        let file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&path)?;
        Ok(Self {
            path,
            writer: BufWriter::new(file),
        })
    }

    pub fn append_submit(&mut self, order: &Order) -> std::io::Result<()> {
        self.append(&WalEntry::Submit(order.clone()))
    }

    pub fn append_cancel(&mut self, symbol: &str, order_id: i64) -> std::io::Result<()> {
        self.append(&WalEntry::Cancel {
            symbol: symbol.to_string(),
            order_id,
        })
    }

    fn append(&mut self, entry: &WalEntry) -> std::io::Result<()> {
        let json = serde_json::to_vec(entry).map_err(|e| {
            std::io::Error::new(std::io::ErrorKind::InvalidData, e)
        })?;
        self.writer.write_all(&(json.len() as u32).to_le_bytes())?;
        self.writer.write_all(&json)?;
        self.writer.flush()
    }

    pub fn read_all(path: &Path) -> Vec<WalEntry> {
        let file = match File::open(path) {
            Ok(f) => f,
            Err(_) => return vec![],
        };
        let mut reader = BufReader::new(file);
        let mut entries = Vec::new();
        let mut len_buf = [0u8; 4];

        loop {
            if reader.read_exact(&mut len_buf).is_err() {
                break;
            }
            let len = u32::from_le_bytes(len_buf) as usize;
            let mut data = vec![0u8; len];
            if reader.read_exact(&mut data).is_err() {
                break;
            }
            if let Ok(entry) = serde_json::from_slice::<WalEntry>(&data) {
                entries.push(entry);
            }
        }

        entries
    }

    pub fn truncate(&mut self) -> std::io::Result<()> {
        drop(std::mem::replace(
            &mut self.writer,
            BufWriter::new(File::create(&self.path)?),
        ));
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::order::*;
    use rust_decimal::Decimal;

    fn make_order() -> Order {
        Order {
            order_id: 1,
            client_order_id: Some("c1".into()),
            user_id: 100,
            symbol: "BTC-USDT".into(),
            side: OrderSide::Buy,
            order_type: OrderType::Limit,
            time_in_force: TimeInForce::GTC,
            price: Some(Decimal::new(50000, 0)),
            quantity: Decimal::new(15, 1),
            filled_quantity: Decimal::ZERO,
            trigger_price: None,
            take_profit_price: None,
            stop_loss_price: None,
            status: OrderStatus::New,
            sequence_time: 0,
            display_quantity: None,
        }
    }

    #[test]
    fn submit_roundtrip() {
        let dir = std::env::temp_dir().join(format!("apex_wal_sub_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        {
            let mut wal = WalManager::open(&dir, "sub").unwrap();
            wal.append_submit(&make_order()).unwrap();
        }
        let entries = WalManager::read_all(&dir.join("sub.wal"));
        assert_eq!(entries.len(), 1);
        match &entries[0] {
            WalEntry::Submit(o) => assert_eq!(o.order_id, 1),
            _ => panic!("expected Submit"),
        }
        fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn cancel_roundtrip() {
        let dir = std::env::temp_dir().join(format!("apex_wal_can_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        {
            let mut wal = WalManager::open(&dir, "can").unwrap();
            wal.append_cancel("BTC-USDT", 42).unwrap();
        }
        let entries = WalManager::read_all(&dir.join("can.wal"));
        assert_eq!(entries.len(), 1);
        match &entries[0] {
            WalEntry::Cancel { order_id, .. } => assert_eq!(*order_id, 42),
            _ => panic!("expected Cancel"),
        }
        fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn json_serde_roundtrip() {
        let submit = WalEntry::Submit(make_order());
        let bytes = serde_json::to_vec(&submit).unwrap();
        let back: WalEntry = serde_json::from_slice(&bytes).unwrap();
        match back {
            WalEntry::Submit(o) => assert_eq!(o.order_id, 1),
            _ => panic!("expected Submit"),
        }

        let cancel = WalEntry::Cancel {
            symbol: "ETH-USDT".into(),
            order_id: 99,
        };
        let bytes = serde_json::to_vec(&cancel).unwrap();
        let back: WalEntry = serde_json::from_slice(&bytes).unwrap();
        match back {
            WalEntry::Cancel { order_id, .. } => assert_eq!(order_id, 99),
            _ => panic!("expected Cancel"),
        }
    }
}
