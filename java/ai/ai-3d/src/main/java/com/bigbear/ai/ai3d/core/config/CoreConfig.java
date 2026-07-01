package com.bigbear.ai.ai3d.core.config;

import cn.hutool.core.date.DatePattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ext.javatime.deser.InstantDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.InstantSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.jdk.StringSerializer;

@Configuration
public class CoreConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
        objectMapper.registerModule(new JavaTimeModule());
//        Map<String, Object> map = new HashMap<>();
//        map.put("time", LocalDateTime.now());
//        map.put("data", LocalDate.now());
//        objectMapper.writeValueAsString(map);
        return objectMapper;
    }

    /**
     * Date格式化字符串
     */
    private static final String DATE_FORMAT = DatePattern.NORM_DATE_PATTERN; //yyyy-MM-dd
    /**
     * DateTime格式化字符串
     */
    private static final String DATETIME_FORMAT = DatePattern.NORM_DATETIME_PATTERN; //yyyy-MM-dd HH:mm:ss
    /**
     * Time格式化字符串
     */
    private static final String TIME_FORMAT = "HH:mm:ss"; //HH:mm

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);




    @Bean
     public JsonMapperBuilderCustomizer jsonCustomizer() {
        final JsonMapper.Builder builder = JsonMapper.builder();
        final SimpleModule simpleModule = new SimpleModule();

// 序列化-Long 类型为字符串
        simpleModule.addSerializer(Long.class, StringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, StringSerializer.instance);

// 序列化-日期时间指定格式
        simpleModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)));
        simpleModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN)));
        simpleModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(DatePattern.NORM_TIME_PATTERN)));
        simpleModule.addSerializer(Instant.class, InstantSerializer.INSTANCE);

// 反序列化-日期时间指定格式
        simpleModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)));
        simpleModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN)));
        simpleModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DatePattern.NORM_TIME_PATTERN)));
        simpleModule.addDeserializer(Instant.class, InstantDeserializer.INSTANT);

        return b -> builder.addModule(simpleModule);
    }



}
