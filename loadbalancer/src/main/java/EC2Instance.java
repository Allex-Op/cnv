import java.util.ArrayList;
import java.util.List;

/**
 * Represents a web server running instance
 */
public class EC2Instance {
    // Total processing capacity of the VM
    public static long PROCESSING_CAPACITY = 100000000;

    // Instance id
    private String id;

    // The ip of the web server, used to do the communication
    private String ip;

    private long currentCapacity = 0;
    private List<Job> runningJobs = new ArrayList<>();

    /**
     * Code called by the auto-scaler to increase the
     * fleet of web servers.
     */
    public void startInstance() {
        String[] info = AwsHandler.createEC2Instance();
        id = info[0];
        ip = info[1];
    }

    /**
     * Code called by the auto-scaler when it decides
     * to cut the fleet power.
     */
    public void terminateInstance() {
        AwsHandler.terminateEC2Instance(id);
    }

    /**
     * Checks if any of the requests dispatched by the load balancer
     * is almost completing.
     */
    public boolean checkIfAnyJobIsAlmostDone() {
        for (Job runningJob : runningJobs) {
            if(runningJob.isJobAlmostDone())
                return true;
        }
        return false;
    }

    /**
     * Load balancer will remove the job after it gets the response
     * from the web server that is taking care of processing
     * the request.
     */
    public void removeJob(String id) {
        for (Job runningJob : runningJobs) {
            if(runningJob.id.equals(id)) {
                runningJobs.remove(runningJob);
                currentCapacity -= runningJob.expectedCost;
            }
        }
    }

    /**
     *  Adds a new job to this specific "web server instance".
     *  Decision taken by the load balancer.
     */
    public void addJob(Job job) {
        currentCapacity += job.expectedCost;
        runningJobs.add(job);
    }

    /**
     *  Returns a list of the requests currently
     *  being processed.
     */
    public List<Job> getRunningJobs() {
        return runningJobs;
    }

    public String getInstanceId() {
        return id;
    }

    public long getCurrentCapacity() {
        return currentCapacity;
    }

    public String getInstanceIp() {
        return ip;
    }
}
