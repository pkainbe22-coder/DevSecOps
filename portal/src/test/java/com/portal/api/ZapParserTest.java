package com.portal.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZapParserTest {

    @Test
    void sumsInstanceCountsByRisk() {
        String json = """
            { "site": [ { "alerts": [
              { "riskcode": "3", "count": "2" },
              { "riskcode": "2", "count": "1" },
              { "riskcode": "1", "count": "4" },
              { "riskcode": "0", "count": "5" }
            ] } ] }
            """;
        Severity s = ZapParser.parse(parse(json));
        assertEquals(0, s.critical);     // ZAP has no critical
        assertEquals(2, s.high);
        assertEquals(1, s.medium);
        assertEquals(9, s.low);          // Low(4) + Informational(5)
    }

    @Test
    void defaultsCountToOneWhenMissing() {
        String json = """
            { "site": [ { "alerts": [ { "riskcode": "3" } ] } ] }
            """;
        assertEquals(1, ZapParser.parse(parse(json)).high);
    }

    @Test
    void emptyIsZero() {
        assertEquals(0, ZapParser.parse(parse("{}")).total());
    }

    private JsonObject parse(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
