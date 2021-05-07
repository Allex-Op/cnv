import java.util.ArrayList;
import java.util.List;

public class LbStrategy {
    private static List<EC2Instance> instances = new ArrayList<>();

    /**
     *  It distributes the request according to the implemented
     *  algorithms.
     */
    public static void distributeRequest(Job job) {
        instances.size();
    }

    /**
     * It increases the number of running web
     * servers that process requests.
     */
    private synchronized static void increaseFleet() {
        EC2Instance inst = new EC2Instance();
        inst.startInstance();
        instances.add(inst);
    }

    /**
     * Decreases the number of running
     * web servers in case there are more
     * than needed.
     *
     * The decision to delete a web server
     * depends on the distribution as we can't simply
     * terminate an instance that may be running
     * some processes.
     *
     * We must mark a server has not used and wait
     * for it's requests to complete.
     */
    private synchronized static void decreaseFleet(){
        for (EC2Instance instance : instances) {

        }
    }
}
