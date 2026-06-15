package com.portal.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencyCheckParserTest {

    @Test
    void countsBySeverityAcrossDependencies() {
        String json = """
            { "dependencies": [
              { "vulnerabilities": [ { "severity": "CRITICAL" }, { "severity": "high" } ] },
              { "vulnerabilities": [ { "severity": "MEDIUM" }, { "severity": "moderate" }, { "severity": "LOW" } ] },
              { "fileName": "no-vulns.jar" }
            ] }
            """;
        Severity s = DependencyCheckParser.parse(parse(json));
        assertEquals(1, s.critical);
        assertEquals(1, s.high);
        assertEquals(2, s.medium);   // MEDIUM + moderate
        assertEquals(1, s.low);
        assertEquals(5, s.total());
    }

    @Test
    void emptyOrMissingIsZero() {
        assertEquals(0, DependencyCheckParser.parse(parse("{}")).total());
        assertEquals(0, DependencyCheckParser.parse(null).total());
    }

    private JsonObject parse(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
