package com.crypto.arbitrage.providers.mexc.model.account;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class BalanceChangeTypeDeserializer extends JsonDeserializer<BalanceChangeType> {

    @Override
    public BalanceChangeType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        try {
            return BalanceChangeType.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid balance change type: " + value, e);
        }
    }
}
