package com.veloxtrade.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veloxtrade.platform.domain.OrderSide;
import com.veloxtrade.platform.service.EngineClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end API coverage with the C++ engine stubbed out. */
@SpringBootTest
@AutoConfigureMockMvc
class TradingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EngineClient engineClient;

    @BeforeEach
    void stubEngine() {
        given(engineClient.quote()).willReturn(new EngineClient.EngineQuote(
                "VLX", new BigDecimal("187.42"), new BigDecimal("187.39"),
                new BigDecimal("187.45"), 12, System.currentTimeMillis()));
        given(engineClient.depth()).willReturn(new EngineClient.EngineDepth(
                "VLX",
                List.of(new EngineClient.EngineLevel(new BigDecimal("187.39"), 400)),
                List.of(new EngineClient.EngineLevel(new BigDecimal("187.45"), 350)),
                System.currentTimeMillis()));
        given(engineClient.submit(any(OrderSide.class), anyLong(), any(BigDecimal.class)))
                .willAnswer(invocation -> {
                    long quantity = invocation.getArgument(1);
                    BigDecimal price = invocation.getArgument(2);
                    return new EngineClient.EngineOrderResult(42L, "FILLED", quantity, 0L, 8500L,
                            List.of(new EngineClient.EngineFill(price, quantity)),
                            System.currentTimeMillis());
                });
    }

    @Test
    void marketQuoteIsPublic() throws Exception {
        mockMvc.perform(get("/api/market/quote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("VLX"))
                .andExpect(jsonPath("$.price").value(187.42));
    }

    @Test
    void ordersRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"VLX","side":"BUY","quantity":5,"limitPrice":187.42}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenTradeUpdatesPortfolio() throws Exception {
        String token = registerAccount();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"VLX","side":"BUY","quantity":10,"limitPrice":187.42}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.filledQuantity").value(10))
                .andExpect(jsonPath("$.matchLatencyNanos").value(8500));

        String portfolio = mockMvc.perform(get("/api/portfolio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(portfolio);
        assertThat(node.get("cashBalance").asDouble()).isEqualTo(100_000.00 - 1_874.20);
        assertThat(node.get("positions").get(0).get("quantity").asLong()).isEqualTo(10L);
        assertThat(node.get("netLiquidation").asDouble()).isEqualTo(100_000.00);
    }

    @Test
    void oversizedOrderIsRejectedByRiskCheck() throws Exception {
        String token = registerAccount();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"VLX","side":"BUY","quantity":100000,"limitPrice":187.42}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient buying power for this order"));
    }

    @Test
    void shortSellingIsBlocked() throws Exception {
        String token = registerAccount();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"VLX","side":"SELL","quantity":5,"limitPrice":187.42}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void malformedOrderFailsValidation() throws Exception {
        String token = registerAccount();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"vlx","side":"HOLD","quantity":0,"limitPrice":-1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String email = "trader" + UUID.randomUUID() + "@example.com";
        registerAccount(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "definitely-not-the-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsATokenForValidCredentials() throws Exception {
        String email = "trader" + UUID.randomUUID() + "@example.com";
        registerAccount(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private String registerAccount() throws Exception {
        return registerAccount("trader" + UUID.randomUUID() + "@example.com");
    }

    private String registerAccount(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "displayName", "Test Trader"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
