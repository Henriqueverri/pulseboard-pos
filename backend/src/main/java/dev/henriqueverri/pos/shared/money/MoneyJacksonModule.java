package dev.henriqueverri.pos.shared.money;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Money travels as a JSON string ({@code "79.90"}), in both directions. Every {@link BigDecimal} in
 * this API is money, so the rule is registered for the type. A JSON number is rejected on input,
 * the same way the PulseBoard ingestion API rejects it.
 */
@Component
public class MoneyJacksonModule extends SimpleModule {

  public MoneyJacksonModule() {
    super("MoneyJacksonModule");
    addSerializer(BigDecimal.class, new MoneySerializer());
    addDeserializer(BigDecimal.class, new MoneyDeserializer());
  }

  static final class MoneySerializer extends StdScalarSerializer<BigDecimal> {

    MoneySerializer() {
      super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeString(Money.format(value));
    }
  }

  static final class MoneyDeserializer extends StdScalarDeserializer<BigDecimal> {

    MoneyDeserializer() {
      super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (!parser.hasToken(JsonToken.VALUE_STRING)) {
        return (BigDecimal)
            context.handleUnexpectedToken(
                BigDecimal.class,
                parser.currentToken(),
                parser,
                "money must be a decimal string, e.g. \"79.90\"");
      }
      String text = parser.getText().trim();
      try {
        return new BigDecimal(text);
      } catch (NumberFormatException e) {
        return (BigDecimal)
            context.handleWeirdStringValue(BigDecimal.class, text, "not a decimal number");
      }
    }
  }
}
