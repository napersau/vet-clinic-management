package com.vetclinic.clinical.config;

import com.vetclinic.common.event.EventRouting;
import com.vetclinic.common.event.RabbitCommonConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue của clinical-service: &lt;service&gt;.&lt;routing-key&gt;, bền vững, có DLQ. */
@Configuration
public class ClinicalRabbitConfig {

    public static final String SERVICE_NAME = "clinical-service";
    // Phải là hằng số compile-time (dùng trong @RabbitListener)
    public static final String PAYMENT_COMPLETED_QUEUE = SERVICE_NAME + "." + EventRouting.PAYMENT_COMPLETED;

    @Bean
    public Queue paymentCompletedQueue() {
        return RabbitCommonConfig.durableQueueWithDlq(PAYMENT_COMPLETED_QUEUE);
    }

    @Bean
    public Queue paymentCompletedDeadLetterQueue() {
        return RabbitCommonConfig.deadLetterQueue(PAYMENT_COMPLETED_QUEUE);
    }

    @Bean
    public Binding paymentCompletedBinding(TopicExchange clinicEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue()).to(clinicEventsExchange).with(EventRouting.PAYMENT_COMPLETED);
    }

    @Bean
    public Binding paymentCompletedDeadLetterBinding(DirectExchange clinicEventsDeadLetterExchange) {
        return BindingBuilder.bind(paymentCompletedDeadLetterQueue()).to(clinicEventsDeadLetterExchange)
                .with(PAYMENT_COMPLETED_QUEUE);
    }
}
