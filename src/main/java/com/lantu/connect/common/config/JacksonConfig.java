package com.lantu.connect.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.time.DisplayDateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.TimeZone;

/**
 * Jackson 配置：HTTP JSON 中 LocalDateTime / LocalDate / java.util.Date 统一为 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Configuration
public class JacksonConfig {

    /** 与 HTTP 使用同一套序列化规则，避免 JSON 列与 API 时间格式不一致。 */
    @Autowired
    public void registerMybatisJackson(@Lazy ObjectMapper objectMapper) {
        JacksonTypeHandler.setObjectMapper(objectMapper);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        DateTimeFormatter dtf = DisplayDateTimeFormat.FORMATTER;

        SimpleModule lantuTime = new SimpleModule("nexusai-connect-java-time");
        lantuTime.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtf));
        lantuTime.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer(dtf));
        lantuTime.addSerializer(LocalDate.class, new LocalDateAsStartOfDaySerializer(dtf));
        lantuTime.addDeserializer(LocalDate.class, new LenientLocalDateDeserializer(dtf));

        return builder -> {
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            SimpleDateFormat dateFormat = new SimpleDateFormat(DisplayDateTimeFormat.PATTERN);
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            builder.dateFormat(dateFormat);
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            builder.modules(lantuTime);
        };
    }

    /** LocalDate 序列化为当天 00:00:00，与 LocalDateTime 展示一致。 */
    private static final class LocalDateAsStartOfDaySerializer extends JsonSerializer<LocalDate> {
        private final DateTimeFormatter formatter;

        private LocalDateAsStartOfDaySerializer(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.atStartOfDay().format(formatter));
            }
        }
    }

    private static final class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        private final DateTimeFormatter primary;

        private LenientLocalDateTimeDeserializer(DateTimeFormatter primary) {
            this.primary = primary;
        }

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String s = p.getValueAsString();
            if (s == null || s.isBlank()) {
                return null;
            }
            String t = s.trim();
            try {
                return LocalDateTime.parse(t, primary);
            } catch (DateTimeParseException ignored) {
                // 兼容历史 ISO 风格：2026-03-26T10:00:00 / 2026-03-26T10:00:00.123
            }
            try {
                if (t.length() == 10) {
                    return LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
                }
            } catch (DateTimeParseException ignored) {
            }
            return LocalDateTime.parse(t, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    private static final class LenientLocalDateDeserializer extends JsonDeserializer<LocalDate> {
        private final DateTimeFormatter primary;

        private LenientLocalDateDeserializer(DateTimeFormatter primary) {
            this.primary = primary;
        }

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String s = p.getValueAsString();
            if (s == null || s.isBlank()) {
                return null;
            }
            String t = s.trim();
            try {
                if (t.length() >= 19 && t.charAt(10) == ' ') {
                    return LocalDateTime.parse(t, primary).toLocalDate();
                }
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
            return LocalDateTime.parse(t, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        }
    }
}
