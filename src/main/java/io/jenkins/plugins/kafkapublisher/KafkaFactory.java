package io.jenkins.plugins.kafkapublisher;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class KafkaFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaFactory.class);

    public static KafkaProducer<String, String> createProducer(KafkaPublisherBuilder.KafkaConfig kafkaConfig) {
        // create Producer properties
        String username = kafkaConfig.getUsername();
        String password = kafkaConfig.getDecodedPassword();
        String jaasConfig = String.format("org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";", username, password);


        Properties properties = new Properties();
        properties.setProperty("security.protocol", "SASL_PLAINTEXT");
        properties.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        properties.setProperty("sasl.jaas.config", jaasConfig);
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBrokers());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // As producer props may get change, it's better to not reuse producer instance for now.
        KafkaProducer<String, String> producer = null;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            // Kafka uses reflection for loading authentication settings, use its classloader.
            Thread.currentThread().setContextClassLoader(
                    org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
            producer = new KafkaProducer<>(properties);
        } catch (Exception e) {
            LOGGER.error("Exception when creating a Kafka producer: {}", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
        return producer;
    }

    public static void sendMessage(KafkaPublisherBuilder.KafkaConfig kafkaConfig, String key, String data) {
        KafkaProducer<String, String> producer = createProducer(kafkaConfig);
        if (producer == null) {
            throw new IllegalArgumentException("Unable to create a Kafka producer");
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(kafkaConfig.getTopicName(), key, data);
        producer.send(record);
        // Flush and close the producer (important for delivering messages)
        producer.flush();
        producer.close();
    }

    public static void sendMessage(KafkaPublisherBuilder.KafkaConfig kafkaConfig, String key, String data, String topicName) {
        KafkaProducer<String, String> producer = createProducer(kafkaConfig);
        if (producer == null) {
            throw new IllegalArgumentException("Unable to create a Kafka producer");
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(topicName, key, data);
        producer.send(record);
        // Flush and close the producer (important for delivering messages)
        producer.flush();
        producer.close();
    }

}
