package cgl.iotrobots.st.storm;

import backtype.storm.Config;
import backtype.storm.generated.ShellComponent;
import backtype.storm.multilang.BoltMsg;
import backtype.storm.multilang.ShellMsg;
import backtype.storm.task.IBolt;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.ShellProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by supun on 7/12/14.
 */
public class ShellBoltN implements IBolt {
    public static Logger LOG = LoggerFactory.getLogger(ShellBoltN.class);
    Process _subprocess;
    OutputCollector _collector;
    Map<String, Tuple> _inputs = new ConcurrentHashMap<String, Tuple>();

    private String[] _command;
    private ShellProcess _process;
    private volatile boolean _running = true;
    private volatile Throwable _exception;
    private LinkedBlockingQueue _pendingWrites = new LinkedBlockingQueue();
    private Random _rand;

    private Thread _readerThread;
    private Thread _writerThread;

    public ShellBoltN(ShellComponent component) {
        this(component.get_execution_command(), component.get_script());
    }

    public ShellBoltN(String... command) {
        _command = command;
    }

    public void prepare(Map stormConf, TopologyContext context,
                        final OutputCollector collector) {
        Object maxPending = stormConf.get(Config.TOPOLOGY_SHELLBOLT_MAX_PENDING);
        if (maxPending != null) {
            this._pendingWrites = new LinkedBlockingQueue(((Number) maxPending).intValue());
        }
        _rand = new Random();
        _collector = collector;
        _process = new ShellProcess(_command);

        //subprocesses must send their pid first thing
        Number subpid = _process.launch(stormConf, context);
        LOG.info("Launched subprocess with pid " + subpid);

        // reader
        _readerThread = new Thread(new Runnable() {
            public void run() {
                while (_running) {
                    try {
                        ShellMsg shellMsg = _process.readShellMsg();

                        String command = shellMsg.getCommand();

                        if (command == null) continue;

                        if (command.equals("ack")) {
                            handleAck(shellMsg.getId());
                        } else if (command.equals("fail")) {
                            handleFail(shellMsg.getId());
                        } else if (command.equals("error")) {
                            handleError(shellMsg.getMsg());
                        } else if (command.equals("log")) {
                            String msg = shellMsg.getMsg();
                            LOG.info("Shell msg: " + msg);
                        } else if (command.equals("emit")) {
                            handleEmit(shellMsg);
                        }
                    } catch (InterruptedException e) {
                    } catch (Throwable t) {
                        die(t);
                    }
                }
            }
        });

        _readerThread.start();

        _writerThread = new Thread(new Runnable() {
            public void run() {
                while (_running) {
                    try {
                        Object write = _pendingWrites.poll(1, SECONDS);
                        if (write instanceof BoltMsg) {
                            _process.writeBoltMsg((BoltMsg) write);
                        } else if (write instanceof List<?>) {
                            _process.writeTaskIds((List<Integer>) write);
                        } else if (write != null) {
                            throw new RuntimeException("Unknown class type to write: " + write.getClass().getName());
                        }
                    } catch (InterruptedException e) {
                    } catch (Throwable t) {
                        die(t);
                    }
                }
            }
        });

        _writerThread.start();
    }

    public void execute(Tuple input) {
        if (_exception != null) {
            throw new RuntimeException(_exception);
        }

        //just need an id
        String genId = Long.toString(_rand.nextLong());
        _inputs.put(genId, input);
        try {
            BoltMsg boltMsg = new BoltMsg();
            boltMsg.setId(genId);
            boltMsg.setComp(input.getSourceComponent());
            boltMsg.setStream(input.getSourceStreamId());
            boltMsg.setTask(input.getSourceTask());
            boltMsg.setTuple(input.getValues());

            _pendingWrites.put(boltMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error during multilang processing", e);
        }
    }

    public void cleanup() {
        _running = false;
        _process.destroy();
        _inputs.clear();
    }

    private void handleAck(Object id) {
        Tuple acked = _inputs.remove(id);
        if (acked == null) {
            throw new RuntimeException("Acked a non-existent or already acked/failed id: " + id);
        }
        _collector.ack(acked);
    }

    private void handleFail(Object id) {
        Tuple failed = _inputs.remove(id);
        if (failed == null) {
            throw new RuntimeException("Failed a non-existent or already acked/failed id: " + id);
        }
        _collector.fail(failed);
    }

    private void handleError(String msg) {
        _collector.reportError(new Exception("Shell Process Exception: " + msg));
    }

    private void handleEmit(ShellMsg shellMsg) throws InterruptedException {
        List<Tuple> anchors = new ArrayList<Tuple>();
        List<String> recvAnchors = shellMsg.getAnchors();
        if (recvAnchors != null) {
            for (String anchor : recvAnchors) {
                Tuple t = _inputs.get(anchor);
                if (t == null) {
                    throw new RuntimeException("Anchored onto " + anchor + " after ack/fail");
                }
                anchors.add(t);
            }
        }

        if (shellMsg.getTask() == 0) {
            List<Integer> outtasks = _collector.emit(shellMsg.getTuple());
            if (shellMsg.areTaskIdsNeeded()) {
                _pendingWrites.put(outtasks);
            }
        } else {
            _collector.emitDirect((int) shellMsg.getTask(),
                    shellMsg.getTuple());
        }
    }

    private void die(Throwable exception) {
        _exception = exception;
    }
}

