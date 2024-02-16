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

    public static KafkaProducer<String, String> createProducer(String brokers) {
        // create Producer properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
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

    public static void sendMessage(String brokers, String topicName, String key, String data) {
        KafkaProducer<String, String> producer = createProducer(brokers);
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
