package test.PlannerTest;

import cgl.iotrobots.collavoid.GlobalPlanner.GlobalPlanner;
import cgl.iotrobots.collavoid.LocalPlanner.LocalPlanner;
import cgl.iotrobots.collavoid.utils.ROSAgentNode;
import geometry_msgs.PoseStamped;
import geometry_msgs.Twist;
import org.ros.concurrent.CancellableLoop;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.parameter.ParameterTree;
import org.ros.node.topic.Publisher;
import org.ros.rosjava.tf.pubsub.TransformListener;

import javax.media.j3d.Transform3D;
import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hjh on 12/9/14.
 */
public class Planner {
    ConnectedNode node = null;
    ParameterTree params;
    Publisher<Twist> velocityPublisher = null;
    //tf listener
    TransformListener tfl = null;
    //planner
    GlobalPlanner globalPlanner = null;
    LocalPlanner localPlanner = null;
    List<PoseStamped> globalPlan = null;
    Point3d start;
    Point3d goal;
    Quat4d oriGoal, oriStart;

    int id_int;
    int init_robots;
    String id_string;
    String globalFrame = "map";
    Twist cmd_vel;

    int robotNb;
    double posRadius;
    double ctr_period;
    Time lastPublished=new Time();
    // check if all planner is ready
    boolean flagAllReady;

    //test
    long planner_delay =0;

    public Planner(TransformListener tfl, int id) {
        ROSAgentNode agentNode=new ROSAgentNode("planner_robot"+id);
        node=agentNode.getNode();
        params = node.getParameterTree();
        this.tfl = tfl;
        this.id_int = id;
        this.id_string = "robot" + id;
        this.robotNb = params.getInteger("/robotNb");
        this.posRadius = params.getDouble("/posRadius");
        initPlanner();
    }

    private void initPlanner() {
        flagAllReady=false;
        lastPublished=node.getCurrentTime();
        velocityPublisher = node.newPublisher(id_string + "/cmd_vel", Twist._TYPE);
        cmd_vel = velocityPublisher.newMessage();

        globalPlan = new ArrayList<PoseStamped>();
        // delay some time to set the planner
        localPlanner = new LocalPlanner(node, tfl);
        globalPlanner = new GlobalPlanner();

        double step = 2 * Math.PI / robotNb;

        //set start point and goal all in ros coordinate
        start = new Point3d(posRadius * Math.cos(id_int * step), posRadius * Math.sin(id_int * step), 0);
        goal=new Point3d();
        goal.scale(-1, start);

        oriStart=new Quat4d();
        oriGoal=new Quat4d();
        double start_ori = Math.PI + id_int * step;

        Transform3D tf = new Transform3D();
        tf.setIdentity();
        tf.rotZ(start_ori);
        tf.get(oriStart);

        tf.rotZ(Math.PI+start_ori);
        tf.get(oriGoal);

        PoseStamped startPose = node.getTopicMessageFactory().newFromType(PoseStamped._TYPE);
        PoseStamped goalPose = node.getTopicMessageFactory().newFromType(PoseStamped._TYPE);

        startPose.getHeader().setFrameId(globalFrame);
        startPose.getPose().getPosition().setX(start.getX());
        startPose.getPose().getPosition().setY(start.getY());
        startPose.getPose().getOrientation().setX(oriStart.getX());
        startPose.getPose().getOrientation().setY(oriStart.getY());
        startPose.getPose().getOrientation().setZ(oriStart.getZ());
        startPose.getPose().getOrientation().setW(oriStart.getW());

        goalPose.getHeader().setFrameId(globalFrame);
        goalPose.getPose().getPosition().setX(goal.getX());
        goalPose.getPose().getPosition().setY(goal.getY());
        goalPose.getPose().getOrientation().setX(oriGoal.getX());
        goalPose.getPose().getOrientation().setY(oriGoal.getY());
        goalPose.getPose().getOrientation().setZ(oriGoal.getZ());
        goalPose.getPose().getOrientation().setW(oriGoal.getW());

        globalPlanner.makePlan(startPose, goalPose, globalPlan);

        if (!localPlanner.setPlan(globalPlan))
            node.getLog().error("Set global plan error!");

        ctr_period = getCtl_period_();

        init_robots=params.getInteger("initializedPlanners");
        params.set("initializedPlanners",init_robots+1);

        final Thread chkPlannersThd=new Thread(new Runnable() {
            @Override
            public void run() {
                while(params.getInteger("initializedPlanners")<robotNb) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                flagAllReady=true;
            }
        });
        chkPlannersThd.start();

        node.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void loop() throws InterruptedException {

                if (flagAllReady) {
                    if (node.getCurrentTime().toSeconds() - lastPublished.toSeconds() > ctr_period) {
                        long startTime=System.nanoTime();

                        if (localPlanner.computeVelocityCommands(cmd_vel));{
                        velocityPublisher.publish(cmd_vel);

                            long consumingTime=System.nanoTime()-startTime;
                            if (planner_delay <consumingTime){
                                planner_delay =consumingTime;
                                System.out.println("localPlanner "+id_int+" max duration: "+(double) planner_delay /1000000000+"s");
                            }
                        }
                        lastPublished=node.getCurrentTime();
                    }
                }
                Thread.sleep((long) ctr_period * 1000);
            }
        });
    }

    private double getCtl_period_() {
        //control period
        GraphName controller_frequency_param_name;
        controller_frequency_param_name = params.search("/controller_frequency");
        double ctl_period_;
        if (controller_frequency_param_name == null) {
            ctl_period_ = 0.05;
        } else {
            double controller_frequency = 0;
            controller_frequency = params.getDouble(controller_frequency_param_name, 20.0);

            if (controller_frequency > 0) {
                ctl_period_ = 1.0 / controller_frequency;
            } else {
                node.getLog().warn("A controller_frequency less than 0 has been set. Ignoring the parameter, assuming a rate of 20Hz");
                ctl_period_ = 0.05;
            }
        }
        node.getLog().info("Sim period is set to " + String.format("%1$.2f", ctl_period_));
        return ctl_period_;
    }

}