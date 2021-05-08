package pt.tecnico.ulisboa.cnv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a web server running instance
 */
public class EC2Instance {
    // Total processing capacity of the VM
    public static long PROCESSING_CAPACITY = 100000000;
    public static double MAX_THRESHOLD_CAPACITY = PROCESSING_CAPACITY * Configs.ABOVE_PROCESSING_THRESHOLD;

    // Instance id
    private String id;

    // The ip of the web server, used to do the communication
    private String ip;

    // Used to mark the instance as not accepting anymore requests
    private boolean markedForTermination = false;

    // Number of requests currently being processed by the instance
    private int numberOfRequests = 0;

    private int failedHealthChecks = 0;

    private long currentCapacity = 0;
    private List<Job> runningJobs = new ArrayList<>();

    /**
     *  It sends the request to this web server and await
     *  for its response.
     *
     *  TODO: There may be a timeout error on requests that take a long time
     */
    public byte[] executeRequest(Job job, String query) {
        addJob(job);

        try {
            String url = Configs.urlBuild(ip) + "scan?" + query;
            System.out.println("[EC2 Instance] Sending request:" + url + ", to instance: " + id);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            removeJob(job);
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception e) {
            System.out.println("[EC2 Instance] Failed obtaining result of request.");
            return new byte[] {};
        }
    }

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
     *
     * Returns the capacity of the web server after the job is completed.
     */
    public synchronized long checkIfAnyJobIsAlmostDone() {
        for (Job runningJob : runningJobs) {
            if(runningJob.isJobAlmostDone())
                return currentCapacity - runningJob.expectedCost;
        }
        return currentCapacity;
    }

    /**
     * Load balancer will remove the job after it gets the response
     * from the web server that is taking care of processing
     * the request.
     *
     * In case it's the last job being processed by the VM it can
     * also terminate the current EC2 instance.
     */
    private synchronized void removeJob(Job job) {
        numberOfRequests--;
        runningJobs.remove(job);
        currentCapacity -= job.expectedCost;

        // If the auto-scaler marked this instance for termination and the number
        // of requests that is currently processing is 0, then the last job will
        // terminate this instance.
        if(markedForTermination && numberOfRequests == 0)
            terminateInstance();
    }

    /**
     *  Adds a new job to this specific "web server instance".
     *  Decision taken by the load balancer.
     */
    private synchronized void addJob(Job job) {
        numberOfRequests++;
        currentCapacity += job.expectedCost;
        runningJobs.add(job);
    }

    /**
     *  Returns true if the current instance is above the processing threshold
     *  defined.
     */
    public synchronized boolean aboveProcessingThreshold() {
        return currentCapacity > (PROCESSING_CAPACITY * Configs.ABOVE_PROCESSING_THRESHOLD);
    }

    /**
     * Checks if the current capacity of the VM is below the 25% mark threshold,
     * used by the auto-scaler to decide which vm's to mark for termination.
     */
    public synchronized boolean belowProcessingThreshold() {
        return currentCapacity < (PROCESSING_CAPACITY * Configs.BELOW_PROCESSING_THRESHOLD);
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

    public boolean isMarkedForTermination() {
        return markedForTermination;
    }

    public void setMarkedForTermination(boolean markedForTermination) {
        this.markedForTermination = markedForTermination;
    }

    public int getNumberOfRequests() {
        return numberOfRequests;
    }

    public void setFailedHealthChecks(int failedHealthChecks) {
        this.failedHealthChecks = failedHealthChecks;
    }

    public int getFailedHealthChecks() {
        return failedHealthChecks;
    }

    public void incrementFailedHealthChecks() {
        failedHealthChecks++;
    }

    public boolean hasCapacityToProcess(Job job) {
        return currentCapacity + job.expectedCost < PROCESSING_CAPACITY;
    }

    public static double getMaxThresholdCapacity() {
        return MAX_THRESHOLD_CAPACITY;
    }
}
