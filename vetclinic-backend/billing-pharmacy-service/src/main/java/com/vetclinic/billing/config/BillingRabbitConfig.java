package com.vetclinic.billing.config;

import com.vetclinic.common.event.EventRouting;
import com.vetclinic.common.event.RabbitCommonConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue của billing-pharmacy-service: &lt;service&gt;.&lt;routing-key&gt;, bền vững, có DLQ. */
@Configuration
public class BillingRabbitConfig {

    public static final String SERVICE_NAME = "billing-pharmacy-service";
    // Phải là hằng số compile-time (dùng trong @RabbitListener)
    public static final String VISIT_CLOSED_QUEUE = SERVICE_NAME + "." + EventRouting.VISIT_CLOSED_TO_PAYMENT;

    @Bean
    public Queue visitClosedQueue() {
        return RabbitCommonConfig.durableQueueWithDlq(VISIT_CLOSED_QUEUE);
    }

    @Bean
    public Queue visitClosedDeadLetterQueue() {
        return RabbitCommonConfig.deadLetterQueue(VISIT_CLOSED_QUEUE);
    }

    @Bean
    public Binding visitClosedBinding(TopicExchange clinicEventsExchange) {
        return BindingBuilder.bind(visitClosedQueue()).to(clinicEventsExchange).with(EventRouting.VISIT_CLOSED_TO_PAYMENT);
    }

    @Bean
    public Binding visitClosedDeadLetterBinding(DirectExchange clinicEventsDeadLetterExchange) {
        return BindingBuilder.bind(visitClosedDeadLetterQueue()).to(clinicEventsDeadLetterExchange)
                .with(VISIT_CLOSED_QUEUE);
    }
}
