package com.vetclinic.appointment.event;

import com.vetclinic.common.event.EventRouting;
import com.vetclinic.common.event.RabbitCommonConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue của appointment-service. Theo mẫu: &lt;service&gt;.&lt;routing-key&gt;, bền vững, có DLQ.
 */
@Configuration
public class AppointmentRabbitConfig {

    public static final String SERVICE_NAME = "appointment-service";
    // Phải là hằng số compile-time (dùng trong @RabbitListener), nên ghép từ hằng số chứ không gọi EventRouting.queueName()
    public static final String VISIT_CHECKED_IN_QUEUE = SERVICE_NAME + "." + EventRouting.VISIT_CHECKED_IN;

    @Bean
    public Queue visitCheckedInQueue() {
        return RabbitCommonConfig.durableQueueWithDlq(VISIT_CHECKED_IN_QUEUE);
    }

    @Bean
    public Queue visitCheckedInDeadLetterQueue() {
        return RabbitCommonConfig.deadLetterQueue(VISIT_CHECKED_IN_QUEUE);
    }

    @Bean
    public Binding visitCheckedInBinding(TopicExchange clinicEventsExchange) {
        return BindingBuilder.bind(visitCheckedInQueue()).to(clinicEventsExchange).with(EventRouting.VISIT_CHECKED_IN);
    }

    @Bean
    public Binding visitCheckedInDeadLetterBinding(DirectExchange clinicEventsDeadLetterExchange) {
        return BindingBuilder.bind(visitCheckedInDeadLetterQueue()).to(clinicEventsDeadLetterExchange)
                .with(VISIT_CHECKED_IN_QUEUE);
    }
}
