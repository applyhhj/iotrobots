package cgl.iotrobots.sim.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

public class RabbitMQSender {
    private static Logger LOG = LoggerFactory.getLogger(RabbitMQSender.class);

    private Channel channel;

    private Connection conn;

    private BlockingQueue<MessageContext> outQueue;

    private String exchangeName;

    private String routingKey;

    private String queueName;

    private String url;

    private ExecutorService executorService;

    public RabbitMQSender(BlockingQueue<MessageContext> outQueue,
                          String exchangeName,
                          String routingKey,
                          String queueName,
                          String url) {
        this.outQueue = outQueue;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.url = url;
        this.queueName = queueName;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void start() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        try {
            factory.setUri(url);
            if (executorService != null) {
                conn = factory.newConnection(executorService);
            } else {
                conn = factory.newConnection();
            }

            channel = conn.createChannel();
            channel.exchangeDeclare(exchangeName, "direct", false);
            channel.queueDeclare(this.queueName, false, false, true, null);
            channel.queueBind(queueName, exchangeName, routingKey);

            Thread t = new Thread(new Worker());
            t.start();
        } catch (IOException e) {
            String msg = "Error creating the RabbitMQ channel";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "Error creating the RabbitMQ channel";
            LOG.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public void stop() {
        try {
            channel.queueDelete(queueName, true, false);
            channel.close();
            conn.close();
        } catch (IOException e) {
            LOG.error("Error closing the rabbit MQ connection", e);
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            boolean run = true;
            int errorCount = 0;
            while (run) {
                try {
                    try {
                        MessageContext input = outQueue.take();

                        Map<String, Object> props = new HashMap<String, Object>();
                        for (Map.Entry<String, Object> e : input.getProperties().entrySet()) {
                            props.put(e.getKey(), e.getValue());
                        }
                        channel.basicPublish(exchangeName, routingKey,
                                new AMQP.BasicProperties.Builder().headers(props).build(), input.getBody());
                    } catch (InterruptedException e) {
                        LOG.error("Exception occurred in the worker listening for consumer changes", e);
                    }
                } catch (Throwable t) {
                    errorCount++;
                    if (errorCount <= 3) {
                        LOG.error("Error occurred " + errorCount + " times.. trying to continue the worker", t);
                    } else {
                        LOG.error("Error occurred " + errorCount + " times.. terminating the worker", t);
                        run = false;
                    }
                }
            }
            String message = "Unexpected notification type";
            LOG.error(message);
            throw new RuntimeException(message);

        }
    }
}