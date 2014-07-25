package cgl.iotrobots.st.storm;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DecodingBolt extends BaseRichBolt {
    private static Logger LOG = LoggerFactory.getLogger(DecodingBolt.class);

    private Decoder decoder;

    private BlockingQueue<DecoderMessage> messages;

    private OutputCollector outputCollector;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.messages = new ArrayBlockingQueue<DecoderMessage>(1024);
        this.outputCollector = outputCollector;
        this.decoder = new Decoder(messages);

        Thread sendThread = new Thread(new SendingThread());
        sendThread.start();
    }

    @Override
    public void execute(Tuple tuple) {
        DecoderMessage message = new DecoderMessage((byte [])tuple.getValueByField(Constants.FRAME_FIELD),
                (Long)tuple.getValueByField(Constants.TIME_FIELD));
        this.decoder.write(message);
        this.outputCollector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields(Constants.FRAME_FIELD, Constants.TIME_FIELD));
    }

    private class SendingThread implements Runnable {
        @Override
        public void run() {
            try {
                DecoderMessage message = messages.take();
                List<Object> list = new ArrayList<Object>();
                list.add(message.getMessage());
                list.add(message.getTime());

                outputCollector.emit(list);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
