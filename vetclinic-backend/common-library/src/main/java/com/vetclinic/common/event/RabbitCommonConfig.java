package com.vetclinic.common.event;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exchange dùng chung + Dead Letter Exchange + JSON converter.
 * Queue của consumer khai báo trong từng service bằng {@link #durableQueueWithDlq(String)}.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
public class RabbitCommonConfig {

    @Bean
    public TopicExchange clinicEventsExchange() {
        return new TopicExchange(EventRouting.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange clinicEventsDeadLetterExchange() {
        return new DirectExchange(EventRouting.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("com.vetclinic.common.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * Queue bền vững, message lỗi (sau khi hết retry) được đẩy sang &lt;queue&gt;.dlq qua clinic.events.dlx.
     * Dùng kèm {@link #deadLetterQueue(String)} và Binding tới DEAD_LETTER_EXCHANGE với routing key = tên queue.
     */
    public static Queue durableQueueWithDlq(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", EventRouting.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", queueName)
                .build();
    }

    public static Queue deadLetterQueue(String queueName) {
        return QueueBuilder.durable(queueName + EventRouting.DEAD_LETTER_SUFFIX).build();
    }
}
