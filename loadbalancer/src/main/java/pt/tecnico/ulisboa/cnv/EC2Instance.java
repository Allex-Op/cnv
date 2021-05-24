package pt.tecnico.ulisboa.cnv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a web server running instance
 */
public class EC2Instance {
    // Instance id
    private String id;

    // The ip of the web server, used to do the communication
    private String ip;

    // Used to mark the instance as not accepting anymore requests
    private AtomicBoolean markedForTermination = new AtomicBoolean(false);

    // Number of requests currently being processed by the instance
    private AtomicInteger numberOfRequests = new AtomicInteger(0);

    private AtomicInteger failedHealthChecks = new AtomicInteger(0);

    private AtomicLong currentCapacity = new AtomicLong(0);

    private ConcurrentHashMap<String, Job> runningJobs = new ConcurrentHashMap<>();

    private AtomicLong creationTimestamp = new AtomicLong(0);

    public EC2Instance() {
        this.creationTimestamp.set(System.currentTimeMillis());
    }

    /**
     *  It sends the request to this web server and await
     *  for its response.
     */
    public byte[] executeRequest(Job job, String query) {
        waitIfFreshInstance();

        try {
            String url = Configs.urlBuild(getInstanceIp()) + "scan?" + query + "&requestId=" + job.id;
            System.out.println("[EC2 Instance] Sending request: " + url + ", to instance: " + id);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            byte[] result = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            return result;
        } catch (Exception e) {
            System.out.println("[EC2 Instance] Failed obtaining result of request: " + e.getMessage());
            return null;
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
     *  When a new requests demands the creation of a VM, the VM is not instantly ready and the
     *  client must wait for it to become available or the request will fail.
     */
    private void waitIfFreshInstance() {
        if(isFreshInstance()) {
            try {
                System.out.println("[EC2 Instance] Instance freshly created, waiting 20 seconds before sending the request..." + Instant.now().getEpochSecond());
                Thread.sleep(Configs.WAIT_TIME_BEFORE_INSTANCE_AVAILABLE);
                System.out.println("[EC2 Instance] Waking up, instance should be prepared by now..." + Instant.now().getEpochSecond());
            } catch (InterruptedException e) {
                System.out.println("[EC2 Instance] Waiting thread interrupted...");
            }
        }
    }

    public boolean isFreshInstance() {
        return (System.currentTimeMillis() - creationTimestamp.get()) < Configs.WAIT_TIME_BEFORE_INSTANCE_AVAILABLE;
    }

    /**
     * Code called by the auto-scaler when it decides
     * to cut the fleet power.
     */
    public synchronized void terminateInstance() {
        AwsHandler.terminateEC2Instance(id);
    }

    /**
     *  Adds a new job to this specific "web server instance".
     *  Decision taken by the load balancer.
     */
    public void addJob(Job job) {
        numberOfRequests.incrementAndGet();
        currentCapacity.set(currentCapacity.get() + job.expectedCost);
        //System.out.println("[EC2 Instance] A new job with expected cost " + job.expectedCost + " was assigned to the VM with ID: " + id + ". The new current capacity is: " + currentCapacity);
        runningJobs.putIfAbsent(job.id, job);
    }

    /**
     * Load balancer will remove the job after it gets the response
     * from the web server that is taking care of processing
     * the request.
     *
     */
    public void removeJob(Job job) {
        runningJobs.remove(job.id);
        numberOfRequests.decrementAndGet();
        currentCapacity.set(currentCapacity.get() - job.expectedCost);
        //System.out.println("[EC2 Instance] A Job is being removed from the instance with id: " + id + ", the job expected cost was:" + job.expectedCost + ". The new current capacity is: " + currentCapacity);
    }

    /**
     * Queries the executed metrics of all jobs
     * that have an higher cost than 5% of the VM capacity.
     */
    public void queryExecutedMetrics() {
        for (Job runningJob : runningJobs.values()) {
            if (runningJob.expectedCost > Configs.LIGHT_REQUEST_THRESHOLD) {
                String url = Configs.urlBuild(getInstanceIp()) + "metrics?requestId=" + runningJob.id;
                System.out.println("[EC2 Instance] Sending executed metrics query to: " + url);

                String response = sendExecutedMetricsHttpRequest(url);

                // If response is empty then the web server handler or sendExecutedMetricsHttpRequest failed
                // for some reason. (It could be that the thread stats are not existent anymore for this job or
                // wasn't able to contact the web server)
                if (response.equals("")) {
                    continue;
                } else {
                    System.out.println("[EC2 Instance] Received current metrics for requestId=" + runningJob.id);
                    runningJob.updateExecutedMetrics(new ExecutedMetrics(response));
                }
            }
        }
    }

    private String sendExecutedMetricsHttpRequest(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            System.out.println("[Instance Manager] Failed sending the executed metrics query: " + e.getMessage());
            return "";
        }
    }


    /************************************************************/
    /*                  Capacity Checker Functions              */
    /************************************************************/

    /**
     * Checks if any of the requests dispatched by the load balancer
     * is almost completing (by observing the executed metrics).
     *
     * Returns the capacity of the web server after the jobs that
     * are about to complete, complete.
     */
    public synchronized long getCapacityAfterConcludingJobsFinish() {
        long newCapacityAfterFreedJobs = currentCapacity.get();
        for (Job runningJob : runningJobs.values()) {
            if (runningJob.isJobAlmostDone())
                newCapacityAfterFreedJobs -= runningJob.expectedCost;
        }
        return newCapacityAfterFreedJobs;
    }

    /**
     *   Checks if this instance can process another request (Job).
     */
    public boolean hasCapacityToProcess(Job job) {
        return (currentCapacity.get() + job.expectedCost) < Configs.MAX_THRESHOLD_CAPACITY;
    }

    /**
     *  Returns true if the current instance is above the processing threshold
     *  defined.
     */
    public boolean aboveProcessingThreshold() {
        return currentCapacity.get() > (Configs.VM_PROCESSING_CAPACITY * Configs.ABOVE_PROCESSING_THRESHOLD);
    }

    /**
     * Checks if the current capacity of the VM is below the 25% mark threshold,
     * used by the auto-scaler to decide which vm's to mark for termination.
     */
    public boolean belowProcessingThreshold() {
        return currentCapacity.get() < (Configs.VM_PROCESSING_CAPACITY * Configs.BELOW_PROCESSING_THRESHOLD);
    }

    public long getCurrentCapacity() {
        return currentCapacity.get();
    }



    /************************************************************/
    /*                      Getters & Setters                   */
    /************************************************************/

    public void setId(String id) {
        this.id = id;
    }

    public String getInstanceId() {
        return id;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     *  The public IP address of the instance is loaded
     *  lazily because its not immediately available after
     *  instance startup.
     */
    public synchronized String getInstanceIp() {
        if(ip == null || ip.equals("")) {
            ip = AwsHandler.getPublicIpOfInstance(id);
            if(ip.equals(""))
                System.out.println("[EC2 Instance] Couldn't obtain IP of the instance with ID: " + id);
        }

        return ip;
    }

    public boolean isMarkedForTermination() {
        return markedForTermination.get();
    }

    public void setMarkedForTermination(boolean markedForTermination) {
        this.markedForTermination.set(markedForTermination);
    }

    public long getCreationTimestamp() {
        return creationTimestamp.get();
    }

    public int getNumberOfRequests() {
        return numberOfRequests.get();
    }

    public void setFailedHealthChecks(int failedHealthChecks) {
        this.failedHealthChecks.set(failedHealthChecks);
    }

    public int getFailedHealthChecks() {
        return failedHealthChecks.get();
    }

    /**
     * Incremented by the auto-scaler when the instance failed to answer
     * an health check request.
     */
    public void incrementFailedHealthChecks() {
        failedHealthChecks.incrementAndGet();
    }

    public void setCreationTimestamp(long creationTimestamp) {
        this.creationTimestamp.set(creationTimestamp);
    }
}
