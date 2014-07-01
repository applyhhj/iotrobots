package cgl.iotrobots.st;

import cgl.iotcloud.core.*;
import cgl.iotcloud.core.sensorsite.SiteContext;
import cgl.iotcloud.core.transport.Channel;
import cgl.iotcloud.core.transport.Direction;
import cgl.iotcloud.core.transport.IdentityConverter;
import cgl.iotcloud.core.transport.MessageConverter;
import cgl.iotcloud.transport.rabbitmq.RabbitMQMessage;
import cgl.iotrobots.st.commons.CommonsUtils;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class STSensor extends AbstractSensor {
    private static Logger LOG = LoggerFactory.getLogger(STSensor.class);

    public static final String BROKER_URL = "broker_url";

    private DroneMessageReceiver videoReceiver;

    private DroneMessageSender controlSender;

    BlockingQueue receivingQueue = new LinkedBlockingQueue();
    BlockingQueue sendingQueue = new LinkedBlockingQueue();

    public static void main(String[] args) {
        Map<String, String> properties = getProperties(args);
        SensorSubmitter.submitSensor(properties, "iotrobots-turtlebot-1.0-SNAPSHOT-jar-with-dependencies.jar", STSensor.class.getCanonicalName(), Arrays.asList("local-1"));
    }

    @Override
    public Configurator getConfigurator(Map map) {
        return new STSensorConfigurator();
    }

    @Override
    public void open(SensorContext context) {
        final Channel sendChannel = context.getChannel("rabbitmq", "sender");
        final Channel receiveChannel = context.getChannel("rabbitmq", "receiver");

        String brokerURL = (String) context.getProperty(BROKER_URL);

        videoReceiver = new DroneMessageReceiver(receivingQueue, "drone_frames", null, null, brokerURL);
        videoReceiver.setExchangeName("drone");
        videoReceiver.setRoutingKey("drone_frames");
        videoReceiver.start();


        controlSender = new DroneMessageSender(sendingQueue, "drone", "control", "control", null, null, brokerURL);
        controlSender.start();

        startSend(sendChannel, receivingQueue);

        startListen(receiveChannel, new MessageReceiver() {
            @Override
            public void onMessage(Object message) {
                if (message instanceof RabbitMQMessage) {
                    try {
                        sendingQueue.put(((RabbitMQMessage) message).getBody());
                    } catch (InterruptedException e) {
                        LOG.error("Failed to put the message for sending", e);
                    }
                }
            }
        });
        LOG.info("Received request {}", context.getId());
    }

    @Override
    public void close() {
        super.close();
        if (videoReceiver != null) {
            videoReceiver.stop();
        }
    }

    private class STSensorConfigurator extends AbstractConfigurator {
        @Override
        public SensorContext configure(SiteContext siteContext, Map conf) {
            String brokerUrl = (String) conf.get(BROKER_URL);

            SensorContext context = new SensorContext(new SensorId("turtle_sensor", "general"));
            context.addProperty(BROKER_URL, brokerUrl);
            Map sendProps = new HashMap();
            sendProps.put("exchange", "storm_drone");
            sendProps.put("routingKey", "storm_drone_frame");
            sendProps.put("queueName", "storm_drone_frame");
            Channel sendChannel = createChannel("sender", sendProps, Direction.OUT, 1024, new IdentityConverter());

            Map receiveProps = new HashMap();
            receiveProps.put("queueName", "storm_control");
            receiveProps.put("exchange", "storm_drone");
            receiveProps.put("routingKey", "storm_control");
            Channel receiveChannel = createChannel("receiver", receiveProps, Direction.IN, 1024, new IdentityConverter());

            context.addChannel("rabbitmq", sendChannel);
            context.addChannel("rabbitmq", receiveChannel);

            return context;
        }
    }

    private class ControlConverter implements MessageConverter {
        @Override
        public Object convert(Object input, Object context) {
            if (input instanceof RabbitMQMessage) {
                try {
                    return CommonsUtils.jsonToMotion(((RabbitMQMessage) input).getBody());
                } catch (IOException e) {
                    LOG.error("Failed to convert the message to a Motion", e);
                    return null;
                }
            }
            return null;
        }
    }

    private static Map<String, String> getProperties(String []args) {
        Map<String, String> conf = new HashMap<String, String>();
        Options options = new Options();
        options.addOption("url", true, "URL of the AMQP Broker");

        CommandLineParser commandLineParser = new BasicParser();
        try {
            CommandLine cmd = commandLineParser.parse(options, args);

            String url = cmd.getOptionValue("url");
            conf.put(BROKER_URL, url);

            return conf;
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("sensor", options );
        }
        return null;
    }
}