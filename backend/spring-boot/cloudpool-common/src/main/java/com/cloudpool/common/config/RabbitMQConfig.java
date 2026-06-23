package com.cloudpool.common.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FILE_PROCESSING_QUEUE = "cloudpool.file.processing";
    public static final String EMBEDDING_QUEUE = "cloudpool.embedding";
    public static final String BACKUP_QUEUE = "cloudpool.backup";
    public static final String CLEANUP_QUEUE = "cloudpool.cleanup";

    public static final String EXCHANGE = "cloudpool.exchange";
    public static final String DLX_EXCHANGE = "cloudpool.dlx";
    public static final String FILE_PROCESSING_DLQ = "cloudpool.file.processing.dlq";
    public static final String EMBEDDING_DLQ_NAME = "cloudpool.embedding.dlq";

    public static final String FILE_ROUTING_KEY = "cloudpool.file.*";
    public static final String EMBEDDING_ROUTING_KEY = "cloudpool.embedding.*";
    public static final String BACKUP_ROUTING_KEY = "cloudpool.backup.*";
    public static final String CLEANUP_ROUTING_KEY = "cloudpool.cleanup.*";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue fileProcessingDlq() {
        return QueueBuilder.durable(FILE_PROCESSING_DLQ).build();
    }

    @Bean
    public Queue embeddingDlq() {
        return QueueBuilder.durable(EMBEDDING_DLQ_NAME).build();
    }

    @Bean
    public Binding fileDlqBinding(Queue fileProcessingDlq, TopicExchange dlxExchange) {
        return BindingBuilder.bind(fileProcessingDlq)
            .to(dlxExchange)
            .with("cloudpool.file.dlq");
    }

    @Bean
    public Binding embeddingDlqBinding(Queue embeddingDlq, TopicExchange dlxExchange) {
        return BindingBuilder.bind(embeddingDlq)
            .to(dlxExchange)
            .with("cloudpool.embedding.dlq");
    }

    @Bean
    public Queue fileProcessingQueue() {
        return QueueBuilder.durable(FILE_PROCESSING_QUEUE)
            .withArgument("x-message-ttl", 3600000)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "cloudpool.file.dlq")
            .withArgument("x-max-length", 10000)
            .withArgument("x-overflow", "reject-publish")
            .build();
    }

    @Bean
    public Binding fileBinding(Queue fileProcessingQueue, TopicExchange exchange) {
        return BindingBuilder.bind(fileProcessingQueue)
            .to(exchange)
            .with(FILE_ROUTING_KEY);
    }

    @Bean
    public Queue embeddingQueue() {
        return QueueBuilder.durable(EMBEDDING_QUEUE)
            .withArgument("x-message-ttl", 3600000)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "cloudpool.embedding.dlq")
            .withArgument("x-max-length", 10000)
            .withArgument("x-overflow", "reject-publish")
            .build();
    }

    @Bean
    public Binding embeddingBinding(Queue embeddingQueue, TopicExchange exchange) {
        return BindingBuilder.bind(embeddingQueue)
            .to(exchange)
            .with(EMBEDDING_ROUTING_KEY);
    }

    @Bean
    public Queue backupQueue() {
        return QueueBuilder.durable(BACKUP_QUEUE)
            .withArgument("x-message-ttl", 86400000)
            .build();
    }

    @Bean
    public Binding backupBinding(Queue backupQueue, TopicExchange exchange) {
        return BindingBuilder.bind(backupQueue)
            .to(exchange)
            .with(BACKUP_ROUTING_KEY);
    }

    @Bean
    public Queue cleanupQueue() {
        return QueueBuilder.durable(CLEANUP_QUEUE)
            .withArgument("x-message-ttl", 86400000)
            .build();
    }

    @Bean
    public Binding cleanupBinding(Queue cleanupQueue, TopicExchange exchange) {
        return BindingBuilder.bind(cleanupQueue)
            .to(exchange)
            .with(CLEANUP_ROUTING_KEY);
    }
}
