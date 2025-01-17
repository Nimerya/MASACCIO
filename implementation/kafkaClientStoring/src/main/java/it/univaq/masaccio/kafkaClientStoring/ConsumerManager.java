package it.univaq.masaccio.kafkaClientStoring;

import it.univaq.masaccio.kafkaClientStoring.dao.implementation.MasaccioDaoMongoDBImpl;
import it.univaq.masaccio.kafkaClientStoring.dao.implementation.MasaccioDaoMySQLImpl;
import it.univaq.masaccio.kafkaClientStoring.dao.interfaces.MasaccioDaoMongoDB;
import it.univaq.masaccio.kafkaClientStoring.dao.interfaces.MasaccioDaoMySQL;
import it.univaq.masaccio.kafkaClientStoring.model.Area;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


public class ConsumerManager {

    //logger
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerManager.class);

    private KafkaConsumer<String, String> consumer;
    private Properties properties;
    private MasaccioDaoMongoDB mongo;
    private MasaccioDaoMySQL mysql;

    public ConsumerManager(String address, String groupId){
        // create new properties kafka object
        this.properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, address);
        // we set here the group id
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // disable the autocommit
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // we set here values about the deserializer
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");


        this.consumer = new KafkaConsumer<>(properties);
        this.mongo = new MasaccioDaoMongoDBImpl();
        this.mysql = new MasaccioDaoMySQLImpl();

    }

    /**
     * subscribe to the topics returned by getAreas()
     */
    public void subscribe(){
        List<String> topics = getAreas();

        try {
            LOGGER.info("subscribing...");
            this.consumer.subscribe(topics);
            LOGGER.info("subscribed");

        } catch (KafkaException e) {
            LOGGER.error("Cannot subscribe to topics - {}", e.getMessage());
            if (LOGGER.isDebugEnabled()){
                e.printStackTrace();
            }
        }
    }


    /**
     * retrieves the area list from the mysql db and return the names
     */
    private List<String> getAreas() {
        List<String> out = new ArrayList<>();
        try {
            this.mysql.init(); // to init the connection with sql DB
            List<Area> l = this.mysql.getAreas();
            LOGGER.info("fetched areas");
            for (Area a : l) {
                LOGGER.info("area name: " + a.getName());
                out.add(a.getName());
            }
            this.mysql.close(); // to close the connection
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return out;
    }

    /**
     * consumes all the data from all the subscribed topics
     * @param pollSize size of the single poll
     */
    public void consume(Integer pollSize){
        // take the time
        long startTime = System.currentTimeMillis();
        Integer refresh_time = Integer.parseInt(Main.properties.getProperty("refresh_time"));
        try {
            // if there isn't the mongo connection will fail and will not consume the data.
            mongo.init();
            while(true) {
                // take the time
                long estimatedTime = System.currentTimeMillis() - startTime;
                if (estimatedTime > refresh_time){
                    throw new TimeoutException("timeout reached, refreshing...");
                }
                // poll return a list of records: Each record contains the topic and partition the record came from
                ConsumerRecords<String, String> records = this.consumer.poll(pollSize);
                for (ConsumerRecord<String, String> record : records) {
                    LOGGER.info("consumed record: (topic = {}, partition = {}, offset = {}, key = {}, value = {})\n", record.topic(), record.partition(), record.offset(), record.key(), record.value());
                    mongo.insert(record.value(), record.topic());
                    // in order to say "ok, we saved"
                    consumer.commitAsync();
                }
            }

        } catch (Exception e) {
            LOGGER.error("Exception in record consumption - {}", e.getMessage());

        } finally{
            consumer.close();
            try {
                // I close mongo connection otherwise when the process restart will open the 2° connection
                mongo.close();
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
                if (LOGGER.isDebugEnabled()){
                    e.printStackTrace();
                }
            }
        }
    }


}
