package com.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.kstream.KTable;

import java.util.Arrays;
import java.util.Properties;

public class StreamsStarterApp {

    public static void main(String[] args) {

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "favourite-color");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,Serdes.String().getClass());

        // Disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
        config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0");

        KStreamBuilder builder = new KStreamBuilder();

        KStream<String, String> textLines = builder.stream("favourite-colour-input"); // 1 - stream from Kafka

        KStream<String, String> usersAndColours = textLines
                .filter((key, value) -> value.contains(","))
                .selectKey((key, value) -> value.split(",")[0].toLowerCase())
                .mapValues(value -> value.split(",")[1].toLowerCase())
                .filter((user, colour) -> Arrays.asList("green", "blue", "red").contains(colour));

        usersAndColours.to("user-keys-and-colours");

        KTable<String, String> usersAndColoursTable = builder.table("user-keys-and-colours");

        KTable<String, Long> favouritesColours = usersAndColoursTable
                .groupBy((user, colour) -> new KeyValue<>(colour, colour))
                .count("CountsByColours");

        favouritesColours.to(Serdes.String(), Serdes.Long(), "favourite-colour-output");

        KafkaStreams streams = new KafkaStreams(builder, config);

        // only do this in dev
        streams.cleanUp();
        streams.start();

        //print the topology
        System.out.println(streams.toString());

        //shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
