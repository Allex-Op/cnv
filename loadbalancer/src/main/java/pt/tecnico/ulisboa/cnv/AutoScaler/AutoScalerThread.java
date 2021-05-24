package pt.tecnico.ulisboa.cnv.AutoScaler;
import com.amazonaws.services.ec2.model.Instance;
import pt.tecnico.ulisboa.cnv.AwsHandler;
import pt.tecnico.ulisboa.cnv.Configs;
import pt.tecnico.ulisboa.cnv.EC2Instance;
import pt.tecnico.ulisboa.cnv.InstanceManager;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static pt.tecnico.ulisboa.cnv.InstanceManager.instances;


public class AutoScalerThread extends Thread {
    @Override
    public void run() {
        System.out.println("[Auto Scaler] Auto-Scaler thread started.");
        startupTimestamp = System.currentTimeMillis();
        cpuUsageTimestamp = System.currentTimeMillis();
        desynchronizedInstancesTimestamp = System.currentTimeMillis();

        autoScale();
    }

    static long startupTimestamp = 0;
    static long lastLifeCheckTimestamp = 0;
    static long cpuUsageTimestamp = 0;
    static long desynchronizedInstancesTimestamp = 0;
    static long currentToleranceOnExceededThreshold = 0;

    static boolean recentStartExecutedMetrics = false;
    static boolean firstDesynchronizedTest = true;

    private static void autoScale() {
        while(true) {
            try {
                System.out.println();
                Thread.sleep(5000);
                System.out.println();
                System.out.println("[Auto Scaler] Auto-scaler woke up, currently there are: " +
                        InstanceManager.getInstancesSize() + " total instances and " + InstanceManager.getNumberOfInstancesMarkedForTermination() +
                        " marked for termination.");

                // Obtaining the lock once instead individually for each operation
                // as it can slow down the auto-scaler thread too much...
                synchronized (InstanceManager.instancesLock) {
                    synchronizeAwsInstances();
                    terminateMarkedInstances();
                    doHealthCheck();
                    getExecutedMetrics();
                    getCPUUsage();
                    observeStatusAndAct();
                }
            } catch(InterruptedException e) {
                System.out.println("[Auto Scaler] Thread interrupted.");
            } catch(Exception e) {
                System.out.println("[Auto Scaler] Exception occurred in auto-scaler thread: " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * It will first observe the status of the system (instances overwhelmed, free..)
     * and then do one of the 3 possible actions: INCREASE_FLEET, DECREASE_FLEET or NO_ACTION.
     */
    private static void observeStatusAndAct() {
        // Observe the system status to decide what to do
        AutoScalerAction status = AutoScaler.getSystemStatus();

        if (status.getAction() == AutoScalerActionEnum.NO_ACTION) {
            System.out.println("[Auto Scaler] No auto scaling action this round.");
        } else if (status.getAction() == AutoScalerActionEnum.DECREASE_FLEET) {
            System.out.println("[Auto Scaler] Decreasing fleet power by terminating " + status.getCount() + " instances.");
            AutoScaler.markForTermination(status.getCount());
        } else if (status.getAction() == AutoScalerActionEnum.INCREASE_FLEET) {
            System.out.println("[Auto Scaler] Increasing fleet power with " + status.getCount() + " new instances.");
            AutoScaler.increaseFleet(status.getCount());
        }
    }

    /**
     * It terminates instances that are marked for termination
     * and have no job.
     */
    private static void terminateMarkedInstances() {
        // Check if there are any instances marked for termination without jobs, if so terminate them
        // this can happen in some buggy scenarios where instances start, they have no jobs and are marked
        // for termination. Never actually terminating.

        for (int i = 0; i < Configs.MAXIMUM_FLEET_CAPACITY; i++) {
            EC2Instance instance = instances[i];
            if(instance == null)
                continue;

            if(instance.isMarkedForTermination() && instance.getCurrentCapacity() == 0) {
                System.out.println("[Auto Scaler] Manually terminating instance without jobs that is marked for termination.");
                instance.terminateInstance();
                instances[i] = null;
            }
        }
    }

    /**
     * Gets executed metrics of all jobs above %5 vm capacity
     * every auto-scaler wake up (5s).
     */
    private static void getExecutedMetrics() {
        // Instructs each instance to query the executed metrics of certain requests that pass a threshold.
        // Not all requests are queried for their metrics because it would add unnecessary overhead for small
        // requests if there are many being processed.

        // We must wait some time for the system to stabilize after the start (instances starting, getting ip address, ...)
        // before querying for the executed metrics.

        // The first request should be 30 seconds after startup, but after that it should be every
        // time the auto-scaler thread wakes up.
        if(!recentStartExecutedMetrics) {
            for (int i = 0; i < Configs.MAXIMUM_FLEET_CAPACITY; i++) {
                EC2Instance instance = instances[i];
                if (instance == null)
                    continue;
                instance.queryExecutedMetrics();
            }
        } else {
            if(System.currentTimeMillis() - startupTimestamp > Configs.EXECUTED_METRICS_FIRST_TIME_CHECK) {
                for (int i = 0; i < Configs.MAXIMUM_FLEET_CAPACITY; i++) {
                    EC2Instance instance = instances[i];
                    if (instance == null)
                        continue;
                    instance.queryExecutedMetrics();
                }

                recentStartExecutedMetrics = false;
            }
        }
    }

    /**
     * Gets average CPU usage of EC2 Instance every 180 seconds
     */
    private static void getCPUUsage() {
        long currTime = System.currentTimeMillis();

        if((currTime - cpuUsageTimestamp) > Configs.CPU_USAGE_CHECK_TIME) {
            List<String> instancesAboveThreshold = AwsHandler.getCloudWatchCPUUsage();

            // If the number of instances above processing threshold is equal to the current fleet size
            // then one of two things:
            // - It's an occasional exception therefore we can ignore it, or
            // - If its occurring regularly then our scaling algorithm isn't working as
            // well as we expected. Therefore we need to slightly correct the cost of a typical request
            // to reflect reality.
            if(instancesAboveThreshold.size() == InstanceManager.getInstancesSize()) {
                // Increase the cost of the request
                if(currentToleranceOnExceededThreshold >= Configs.COST_CORRECTION_TOLERANCE) {
                    currentToleranceOnExceededThreshold = 0;
                    Configs.increaseCostCorrection();
                    System.out.println("[Auto Scaler] Cloudwatch CPU observations concluded that the CPU usage threshold is constantly being exceeded, therefore it's increasing the cost of requests, new correction value: " + Configs.COST_CORRECTION_CPU_CURRENT);
                } else {
                    currentToleranceOnExceededThreshold++;
                }
            } else {
                // Decrease the current cost correction of a request if the number of exceeded threshold is 0
                // it will only decrease until the cost_correction variable reaches the base value and won't go lower
                // than that.
                if(currentToleranceOnExceededThreshold > 0) {
                    currentToleranceOnExceededThreshold--;
                    if(currentToleranceOnExceededThreshold == 0) {
                        Configs.decreaseCostCorrection();
                        System.out.println("[Auto Scaler] Cloudwatch CPU observations concluded that the CPU usage threshold is in normal values, therefore it's returning the cost of requests back to the base, new correction value: " + Configs.COST_CORRECTION_CPU_CURRENT);
                    }
                }
            }

            cpuUsageTimestamp = currTime;
        }
    }

    /**
     * It checks if all the instances registered
     * are still alive. In case they are down the
     * specified object should be deleted.
     */
    private static void doHealthCheck() {
        // Check the status of the VM's every 30 seconds
        if (System.currentTimeMillis() - lastLifeCheckTimestamp > Configs.HEALTH_CHECK_TIME) {

            for (int i = 0; i < Configs.MAXIMUM_FLEET_CAPACITY; i++) {
                EC2Instance instance = instances[i];
                if(instance == null)
                    continue;

                String url = Configs.healthCheckUrlBuild(instance.getInstanceIp());

                System.out.println("[Auto Scaler] Sending health check message to: " + url);
                boolean alive = sendHealthCheck(url);

                if (!alive) {
                    instance.incrementFailedHealthChecks();
                    if (instance.getFailedHealthChecks() > Configs.MAX_FAILED_HEALTH_CHECKS) {
                        System.out.println("[Auto Scaler] Instance removed for failing the maximum health checks.");

                        instances[i] = null;
                    }
                } else {
                    instance.setFailedHealthChecks(0);
                }
            }


            lastLifeCheckTimestamp = System.currentTimeMillis();
        }
    }

    /**
     *  Returns "true" if the instance answered with "alive"
     *  or "false" if no answer.
     */
    private static boolean sendHealthCheck(String url) {
        try {
            Duration dr = Duration.ofSeconds(3000);

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(dr)
                    .build();

            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return response.contains("alive");
        } catch (Exception e) {
            System.out.println("[Auto Scaler] Failed sending HTTP request (healthcheck). Instance unavailable.");
            return false;
        }
    }


    /**
     * Because of unfortunate bugs or sudden terminations
     * some EC2 instances may have been deleted from the EC2 Instances
     * list but still running in some unknown status in AWS.
     *
     * Firstly, it will perform an health check on those instances.
     * If the check completes successfully then they are added back
     * to the instances list.
     *
     * If the health check fails they are terminated.
     */
    private static void synchronizeAwsInstances() {
        long currTime = System.currentTimeMillis();
        if(firstDesynchronizedTest || (currTime - desynchronizedInstancesTimestamp) > Configs.DESYNCHRONIZED_TEST_TIME) {
            firstDesynchronizedTest = false;
            desynchronizedInstancesTimestamp = currTime;

            Set<Instance> instances = AwsHandler.getRunningInstances();
            Set<Instance> desynchronizedInstances = findDesynchronizedInstances(instances);

            for (Instance instance : desynchronizedInstances) {
                String ip = instance.getPublicIpAddress();
                String instanceId = instance.getInstanceId();

                // If it's alive and health check succeeded add it to the running
                // instances list, otherwise terminate the remote AWS instance.
                if (sendHealthCheckToDesyncInstance(ip)) {
                    if (InstanceManager.getInstancesSize() >= Configs.MAXIMUM_FLEET_CAPACITY) {
                        System.out.println("[AutoScaler] A de-synced instance was found alive, but maximum fleet capacity was already reached, terminating it.");
                        AwsHandler.terminateEC2Instance(instanceId);
                    } else {
                        System.out.println("[AutoScaler] A de-synced instance was found alive, adding it back to the fleet.");

                        EC2Instance inst = new EC2Instance();
                        inst.setId(instanceId);
                        inst.setIp(ip);
                        inst.setCreationTimestamp(0);   // Just to avoid the fresh instance process
                        InstanceManager.addInstance(inst);
                    }
                } else {
                    System.out.println("[AutoScaler] Terminating de-synced remote EC2 instance that failed to answer the health check.");
                    AwsHandler.terminateEC2Instance(instanceId);
                }

            }
        }
    }

    /**
     *  Finds from the running list of instances which currently
     *  exist in the context of the load balancer process (only known by AWS), if it doesn't
     *  exist is considered a de-synchronized instance.
     */
    private static Set<Instance> findDesynchronizedInstances(Set<Instance> runningInstances) {
        Set<Instance> desynchronizedInstances = new HashSet<>();

        for (Instance instance : runningInstances) {
            // If the running instance doesn't exist then its considered de-synced
            if(!InstanceManager.doesInstanceExist(instance)) {
                desynchronizedInstances.add(instance);
            }
        }

        return desynchronizedInstances;
    }

    /**
     *  Send HTTP request to health check endpoint of instance with ip @ip.
     */
    private static boolean sendHealthCheckToDesyncInstance(String ip) {
        try {
            String url = Configs.healthCheckUrlBuild(ip);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            String result = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return result.contains("alive");
        } catch (Exception e) {
            System.out.println("[Auto Scaler] Failed sending health check: " + e.getMessage());
            return false;
        }
    }
}
