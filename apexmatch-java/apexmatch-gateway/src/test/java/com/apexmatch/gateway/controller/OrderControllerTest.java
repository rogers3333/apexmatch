package com.apexmatch.gateway.controller;

import com.apexmatch.gateway.config.ServiceConfig;
import com.apexmatch.gateway.config.WebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.apexmatch.gateway.dto.CancelOrderRequest;
import com.apexmatch.gateway.dto.PlaceOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void placeOrderSuccess() throws Exception {
        PlaceOrderRequest req = PlaceOrderRequest.builder()
                .userId(1L).symbol("BTC-USDT").side("BUY").type("LIMIT")
                .timeInForce("GTC").price(new BigDecimal("50000"))
                .quantity(new BigDecimal("1"))
                .build();

        mockMvc.perform(post("/api/v1/order/place")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.affectedOrder").exists());
    }

    @Test
    void placeOrderValidationFail() throws Exception {
        PlaceOrderRequest req = PlaceOrderRequest.builder()
                .userId(null).symbol("").side("BUY").type("LIMIT")
                .quantity(new BigDecimal("1"))
                .build();

        mockMvc.perform(post("/api/v1/order/place")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void cancelOrder() throws Exception {
        PlaceOrderRequest placeReq = PlaceOrderRequest.builder()
                .userId(1L).symbol("BTC-USDT").side("SELL").type("LIMIT")
                .timeInForce("GTC").price(new BigDecimal("60000"))
                .quantity(new BigDecimal("1"))
                .build();

        mockMvc.perform(post("/api/v1/order/place")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(placeReq)));

        CancelOrderRequest cancelReq = CancelOrderRequest.builder()
                .userId(1L).symbol("BTC-USDT").orderId(999L)
                .build();

        mockMvc.perform(post("/api/v1/order/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk());
    }
}
