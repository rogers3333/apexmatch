package com.apexmatch.engine.rust;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA 接口定义，对应 Rust {@code ffi.rs} 中的 C ABI 导出函数。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface RustNativeLibrary extends Library {

    /** 加载 Rust 动态库实例 */
    static RustNativeLibrary load(String libraryPath) {
        return Native.load(libraryPath, RustNativeLibrary.class);
    }

    /** ABI 版本号 */
    int apexmatch_engine_abi_version();

    /** 初始化交易对 */
    void engine_init(String symbol);

    /** 提交订单（JSON 字符串），返回 JSON 结果字符串（调用方需 free） */
    Pointer engine_submit_order_json(String orderJson);

    /** 撤单，返回 1=成功，0=不存在 */
    byte engine_cancel_order(String symbol, long orderId);

    /** 查询盘口深度（JSON），返回 JSON 字符串（调用方需 free） */
    Pointer engine_get_depth_json(String symbol, int levels);

    /** 释放 Rust 分配的字符串 */
    void engine_free_string(Pointer ptr);
}
