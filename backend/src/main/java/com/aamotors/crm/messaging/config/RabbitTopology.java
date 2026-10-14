package com.aamotors.crm.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Durable topology. Every work queue has a dead-letter queue, and a companion
 * retry queue whose messages expire back into the work queue (per-message TTL
 * = exponential back-off computed by the consumer).
 *
 * <pre>
 *  crm.events (topic) ──sms.deliver──▶ sms.deliver ──reject──▶ crm.dlx ──▶ sms.deliver.dlq
 *                                         ▲
 *  sms.deliver.retry (TTL) ───────────────┘ (x-dead-letter-exchange = crm.events)
 * </pre>
 */
@Configuration
public class RabbitTopology {

    public static final String EVENTS_EXCHANGE = "crm.events";
    public static final String DLX = "crm.dlx";

    public static final String SMS_QUEUE = "sms.deliver";
    public static final String SMS_RETRY_QUEUE = "sms.deliver.retry";
    public static final String SMS_DLQ = "sms.deliver.dlq";
    public static final String SMS_ROUTING_KEY = "sms.deliver";

    public static final String ADVERTISING_QUEUE = "advertising.deliver";
    public static final String ADVERTISING_RETRY_QUEUE = "advertising.deliver.retry";
    public static final String ADVERTISING_DLQ = "advertising.deliver.dlq";
    public static final String ADVERTISING_ROUTING_KEY = "advertising.deliver";

    @Bean TopicExchange eventsExchange() { return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build(); }
    @Bean DirectExchange deadLetterExchange() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean Queue smsQueue() {
        return QueueBuilder.durable(SMS_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(SMS_DLQ).build();
    }
    @Bean Queue smsRetryQueue() {
        return QueueBuilder.durable(SMS_RETRY_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", EVENTS_EXCHANGE, "x-dead-letter-routing-key", SMS_ROUTING_KEY)).build();
    }
    @Bean Queue smsDlq() { return QueueBuilder.durable(SMS_DLQ).build(); }
    @Bean Binding smsBinding() { return BindingBuilder.bind(smsQueue()).to(eventsExchange()).with(SMS_ROUTING_KEY); }
    @Bean Binding smsDlqBinding() { return BindingBuilder.bind(smsDlq()).to(deadLetterExchange()).with(SMS_DLQ); }

    @Bean Queue advertisingQueue() {
        return QueueBuilder.durable(ADVERTISING_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(ADVERTISING_DLQ).build();
    }
    @Bean Queue advertisingRetryQueue() {
        return QueueBuilder.durable(ADVERTISING_RETRY_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", EVENTS_EXCHANGE, "x-dead-letter-routing-key", ADVERTISING_ROUTING_KEY)).build();
    }
    @Bean Queue advertisingDlq() { return QueueBuilder.durable(ADVERTISING_DLQ).build(); }
    @Bean Binding advertisingBinding() { return BindingBuilder.bind(advertisingQueue()).to(eventsExchange()).with(ADVERTISING_ROUTING_KEY); }
    @Bean Binding advertisingDlqBinding() { return BindingBuilder.bind(advertisingDlq()).to(deadLetterExchange()).with(ADVERTISING_DLQ); }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(converter);
        t.setMandatory(true);
        return t;
    }
}
