package dev.henriqueverri.pos.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoneyTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new MoneyJacksonModule());

  @Test
  void normalizesToTwoDecimalPlaces() {
    assertThat(Money.of("79.9")).isEqualTo(new BigDecimal("79.90"));
    assertThat(Money.of("10")).isEqualTo(new BigDecimal("10.00"));
    assertThat(Money.of("5.000")).isEqualTo(new BigDecimal("5.00"));
  }

  @Test
  void neverRoundsSilently() {
    assertThatThrownBy(() -> Money.of("10.005")).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void lineTotalIsExact() {
    assertThat(Money.multiply(new BigDecimal("79.90"), 2)).isEqualTo(new BigDecimal("159.80"));
    assertThat(Money.multiply(new BigDecimal("0.10"), 3)).isEqualTo(new BigDecimal("0.30"));
    assertThat(Money.multiply(new BigDecimal("2399.90"), 10_000))
        .isEqualTo(new BigDecimal("23999000.00"));
  }

  @Test
  void sumOfLinesHasNoFloatingPointDrift() {
    List<BigDecimal> lines = java.util.Collections.nCopies(10, new BigDecimal("0.10"));

    assertThat(Money.sum(lines)).isEqualTo(new BigDecimal("1.00"));
    assertThat(Money.sum(List.of())).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void detectsValuesAboveTheColumnLimit() {
    assertThat(Money.exceedsMax(new BigDecimal("9999999999.99"))).isFalse();
    assertThat(Money.exceedsMax(new BigDecimal("10000000000.00"))).isTrue();
  }

  @Test
  void serializesAsDecimalString() throws Exception {
    record Payload(BigDecimal total) {}

    JsonNode json =
        mapper.readTree(mapper.writeValueAsString(new Payload(new BigDecimal("409.7"))));

    assertThat(json.get("total").isTextual()).isTrue();
    assertThat(json.get("total").asText()).isEqualTo("409.70");
    assertThat(mapper.writeValueAsString(new Payload(new BigDecimal("1E+3"))))
        .isEqualTo("{\"total\":\"1000.00\"}");
  }

  record Input(BigDecimal price) {}

  @Test
  void deserializesFromStringAndRejectsJsonNumbers() throws Exception {
    assertThat(mapper.readValue("{\"price\":\"79.90\"}", Input.class).price())
        .isEqualTo(new BigDecimal("79.90"));
    assertThat(mapper.readValue("{\"price\":null}", Input.class).price()).isNull();

    assertThatThrownBy(() -> mapper.readValue("{\"price\":79.90}", Input.class))
        .isInstanceOf(MismatchedInputException.class);
    assertThatThrownBy(() -> mapper.readValue("{\"price\":\"abc\"}", Input.class))
        .isInstanceOf(MismatchedInputException.class);
  }
}
