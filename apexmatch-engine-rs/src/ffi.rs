//! C ABI 导出层，供 Java JNA 调用。
//!
//! 数据交换使用 bincode 序列化：调用方传入 `*const u8 + len`，
//! 返回 [`FfiBuffer`] 指针 + 长度，由调用方通过 [`engine_free_buffer`] 释放。

use std::ffi::{c_char, CStr};
use std::sync::{Mutex, OnceLock};

use crate::engine::MatchingEngine;
use crate::order::Order;

static ENGINE: OnceLock<Mutex<MatchingEngine>> = OnceLock::new();

fn global_engine() -> &'static Mutex<MatchingEngine> {
    ENGINE.get_or_init(|| Mutex::new(MatchingEngine::new()))
}

/// FFI 返回的字节缓冲区。
#[repr(C)]
pub struct FfiBuffer {
    pub data: *mut u8,
    pub len: usize,
}

impl FfiBuffer {
    fn from_bytes(bytes: Vec<u8>) -> Self {
        let boxed = bytes.into_boxed_slice();
        let ptr = boxed.as_ptr() as *mut u8;
        let len = boxed.len();
        std::mem::forget(boxed);
        FfiBuffer { data: ptr, len }
    }

    fn empty() -> Self {
        FfiBuffer {
            data: std::ptr::null_mut(),
            len: 0,
        }
    }
}

/// ABI 版本号（握手用）。
#[no_mangle]
pub extern "C" fn apexmatch_engine_abi_version() -> u32 {
    2
}

/// 初始化指定交易对。
#[no_mangle]
pub extern "C" fn engine_init(symbol: *const c_char) {
    let symbol = unsafe { CStr::from_ptr(symbol) }
        .to_str()
        .unwrap_or_default();
    global_engine()
        .lock()
        .unwrap()
        .init(symbol.to_string());
}

/// 提交订单（bincode 序列化的 Order），返回 bincode 序列化的 MatchResult。
#[no_mangle]
pub extern "C" fn engine_submit_order(data: *const u8, len: usize) -> FfiBuffer {
    let bytes = unsafe { std::slice::from_raw_parts(data, len) };
    let order: Order = match bincode::deserialize(bytes) {
        Ok(o) => o,
        Err(_) => return FfiBuffer::empty(),
    };
    let result = global_engine().lock().unwrap().submit_order(order);
    match bincode::serialize(&result) {
        Ok(encoded) => FfiBuffer::from_bytes(encoded),
        Err(_) => FfiBuffer::empty(),
    }
}

/// 撤单，返回 1 = 成功，0 = 不存在。
#[no_mangle]
pub extern "C" fn engine_cancel_order(symbol: *const c_char, order_id: i64) -> u8 {
    let symbol = unsafe { CStr::from_ptr(symbol) }
        .to_str()
        .unwrap_or_default();
    let result = global_engine()
        .lock()
        .unwrap()
        .cancel_order(symbol, order_id);
    if result.is_some() { 1 } else { 0 }
}

/// 查询盘口深度，返回 bincode 序列化的 MarketDepth。
#[no_mangle]
pub extern "C" fn engine_get_depth(symbol: *const c_char, levels: i32) -> FfiBuffer {
    let symbol = unsafe { CStr::from_ptr(symbol) }
        .to_str()
        .unwrap_or_default();
    let depth = global_engine()
        .lock()
        .unwrap()
        .get_market_depth(symbol, levels as usize);
    match bincode::serialize(&depth) {
        Ok(encoded) => FfiBuffer::from_bytes(encoded),
        Err(_) => FfiBuffer::empty(),
    }
}

/// 释放由引擎分配的缓冲区。
#[no_mangle]
pub extern "C" fn engine_free_buffer(buf: FfiBuffer) {
    if !buf.data.is_null() && buf.len > 0 {
        unsafe {
            let _ = Box::from_raw(std::slice::from_raw_parts_mut(buf.data, buf.len));
        }
    }
}

// ==================== JSON 接口（供 JNA 调用） ====================

/// 提交订单（JSON 字符串），返回 JSON 字符串（调用方需 engine_free_string 释放）。
#[no_mangle]
pub extern "C" fn engine_submit_order_json(json_ptr: *const c_char) -> *mut c_char {
    let json_str = match unsafe { CStr::from_ptr(json_ptr) }.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let order: Order = match serde_json::from_str(json_str) {
        Ok(o) => o,
        Err(_) => return std::ptr::null_mut(),
    };
    let result = global_engine().lock().unwrap().submit_order(order);
    match serde_json::to_string(&result) {
        Ok(s) => match std::ffi::CString::new(s) {
            Ok(cs) => cs.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(_) => std::ptr::null_mut(),
    }
}

/// 查询盘口深度（JSON），返回 JSON 字符串。
#[no_mangle]
pub extern "C" fn engine_get_depth_json(symbol: *const c_char, levels: i32) -> *mut c_char {
    let symbol = match unsafe { CStr::from_ptr(symbol) }.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let depth = global_engine()
        .lock()
        .unwrap()
        .get_market_depth(symbol, levels as usize);
    match serde_json::to_string(&depth) {
        Ok(s) => match std::ffi::CString::new(s) {
            Ok(cs) => cs.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(_) => std::ptr::null_mut(),
    }
}

/// 释放 Rust 分配的 CString。
#[no_mangle]
pub extern "C" fn engine_free_string(s: *mut c_char) {
    if !s.is_null() {
        unsafe {
            let _ = std::ffi::CString::from_raw(s);
        }
    }
}
