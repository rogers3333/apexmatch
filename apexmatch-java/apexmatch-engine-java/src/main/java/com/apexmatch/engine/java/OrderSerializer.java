package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * 订单的二进制序列化 / 反序列化工具，用于 WAL 与快照持久化，
 * 避免依赖 Java 原生 Serializable 或第三方 JSON 库。
 *
 * @author luka
 * @since 2025-03-26
 */
public final class OrderSerializer {

    private OrderSerializer() {
    }

    /**
     * 将订单写入输出流。
     */
    public static void write(DataOutputStream out, Order order) throws IOException {
        out.writeLong(order.getOrderId());
        writeNullableUTF(out, order.getClientOrderId());
        out.writeLong(order.getUserId() != null ? order.getUserId() : 0L);
        writeNullableUTF(out, order.getSymbol());
        out.writeByte(order.getSide().ordinal());
        out.writeByte(order.getType().ordinal());
        out.writeByte(order.getTimeInForce() != null ? order.getTimeInForce().ordinal() : -1);
        writeNullableBD(out, order.getPrice());
        writeNullableBD(out, order.getQuantity());
        writeNullableBD(out, order.getFilledQuantity());
        writeNullableBD(out, order.getTriggerPrice());
        writeNullableBD(out, order.getTakeProfitPrice());
        writeNullableBD(out, order.getStopLossPrice());
        out.writeByte(order.getStatus() != null ? order.getStatus().ordinal() : -1);
        out.writeLong(order.getSequenceTime());
        writeNullableBD(out, order.getDisplayQuantity());
    }

    /**
     * 从输入流读取订单。
     */
    public static Order read(DataInputStream in) throws IOException {
        Order.OrderBuilder builder = Order.builder();
        builder.orderId(in.readLong());
        builder.clientOrderId(readNullableUTF(in));
        long userId = in.readLong();
        builder.userId(userId == 0 ? null : userId);
        builder.symbol(readNullableUTF(in));
        builder.side(OrderSide.values()[in.readByte()]);
        builder.type(OrderType.values()[in.readByte()]);
        int tifOrdinal = in.readByte();
        builder.timeInForce(tifOrdinal < 0 ? null : TimeInForce.values()[tifOrdinal]);
        builder.price(readNullableBD(in));
        builder.quantity(readNullableBD(in));
        builder.filledQuantity(readNullableBD(in));
        builder.triggerPrice(readNullableBD(in));
        builder.takeProfitPrice(readNullableBD(in));
        builder.stopLossPrice(readNullableBD(in));
        int statusOrdinal = in.readByte();
        builder.status(statusOrdinal < 0 ? null : OrderStatus.values()[statusOrdinal]);
        builder.sequenceTime(in.readLong());
        builder.displayQuantity(readNullableBD(in));
        return builder.build();
    }

    // ==================== 私有工具 ====================

    private static void writeNullableUTF(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeUTF(value);
        }
    }

    private static String readNullableUTF(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeNullableBD(DataOutputStream out, BigDecimal value) throws IOException {
        if (value == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeUTF(value.toPlainString());
        }
    }

    private static BigDecimal readNullableBD(DataInputStream in) throws IOException {
        return in.readBoolean() ? new BigDecimal(in.readUTF()) : null;
    }
}
