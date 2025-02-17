package com.crypto.arbitrage.providers.mexc.model.order;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.math.BigDecimal;

public class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            return new BigDecimal(p.getText());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid BigDecimal value: " + p.getText(), e);
        }
    }
}