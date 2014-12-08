package cgl.iotrobots.slam.streaming.rabbitmq;

import cgl.iotcloud.core.transport.TransportConstants;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

public class RabbitMQReceiver {
    private static Logger LOG = LoggerFactory.getLogger(RabbitMQReceiver.class);

    private Channel channel;

    private Connection conn;

    private BlockingQueue<Message> inQueue;

    private String queueName;

    private String url;

    private ExecutorService executorService;

    private String exchangeName;

    private String routingKey;

    public RabbitMQReceiver(BlockingQueue<Message> inQueue,
                            String queueName,
                            String url) {
        this.inQueue = inQueue;
        this.queueName = queueName;
        this.url = url;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void start() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            factory.setUri(url);
            if (executorService != null) {
                conn = factory.newConnection(executorService);
            } else {
                conn = factory.newConnection();
            }

            channel = conn.createChannel();

            if (exchangeName != null && routingKey != null) {
                channel.exchangeDeclare(exchangeName, "direct", false);
                channel.queueDeclare(this.queueName, false, false, true, null);
                channel.queueBind(queueName, exchangeName, routingKey);
            }

            boolean autoAck = false;
            channel.basicConsume(queueName, false, "myConsumerTag",
                    new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag,
                                                   Envelope envelope,
                                                   AMQP.BasicProperties properties,
                                                   byte[] body)
                                throws IOException {
                            long deliveryTag = envelope.getDeliveryTag();
                            // RabbitMQMessage message = new RabbitMQMessage(properties, body);
                            // get the sensor id from the properties
                            Map<String, Object> props = new HashMap<String, Object>();
                            if (properties != null && properties.getHeaders() != null) {
                                for (Map.Entry<String, Object> e : properties.getHeaders().entrySet()) {
                                    props.put(e.getKey(), e.getValue().toString());
                                }
                            }

                            Message message = new Message(body, props);
                            try {
                                inQueue.put(message);
                            } catch (InterruptedException e) {
                                LOG.error("Failed to put the object to the queue");
                            }
                            channel.basicAck(deliveryTag, false);
                        }
                    });
        } catch (IOException e) {
            String msg = "Error consuming the message";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "Error connecting to broker";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public void stop() {
        try {
            channel.queueDelete(queueName, true, false);

            if (channel != null) {
                channel.close();
            }
            if (conn != null) {
                conn.close();
            }
        } catch (IOException e) {
            LOG.error("Error closing the rabbit MQ connection", e);
        }
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }
}
