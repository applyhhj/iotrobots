package cgl.iotrobots.collavoid.utils;

import org.ros.address.InetAddressFactory;
import org.ros.master.uri.MasterUriProvider;
import org.ros.master.uri.StaticMasterUriProvider;
import org.ros.namespace.GraphName;
import org.ros.node.*;

import java.net.URI;

/**
 * Created by hjh on 11/25/14.
 */
public class ROSAgentNode {

    private PubSubNode pubSubNode;
    private ConnectedNode node;

    public class PubSubNode extends AbstractNodeMain {
        private boolean initialized = false;

        @Override
        public void onStart(ConnectedNode connectedNode) {
            node = connectedNode;
            initialized = true;
        }

        @Override
        public GraphName getDefaultNodeName() {
            return GraphName.of("agent");
        }

        @Override
        public void onShutdown(Node node) {
            node.shutdown();
        }
    }

    public ROSAgentNode(String nodeName) {
        String host = InetAddressFactory.newNonLoopback().getHostAddress()
                .toString();
//        String masterIP="156.56.93.131";
//        String masterIP="149.160.224.207";
        String masterIP="localhost";
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(host,URI.create("http://"+masterIP+":11311") );
        final NodeMainExecutor executor = DefaultNodeMainExecutor.newDefault();
        nodeConfiguration.setNodeName(nodeName);

        pubSubNode = new PubSubNode();
        executor.execute(pubSubNode, nodeConfiguration);

        System.out.println("Initializing Node " + nodeName + "..");

        while (!pubSubNode.initialized) {
            System.out.print(".");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Done!");
    }

    public ConnectedNode getNode() {
        if (pubSubNode.initialized) {
            return node;
        } else {
            System.out.println("Error, node not initialized!");
            return null;
        }
    }


}
