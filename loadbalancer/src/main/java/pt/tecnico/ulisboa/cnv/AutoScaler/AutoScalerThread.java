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
import java.util.Set;


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

    static boolean recentStartExecutedMetrics = false;
    static boolean firstDesynchronizedTest = true;

    private static void autoScale() {
        while(true) {
            try {
                Thread.sleep(5000);
                System.out.println("[Auto Scaler] Auto-scaler woke up, currently there are: " +
                        InstanceManager.getInstancesSize() + " total instances and " + InstanceManager.getNumberOfInstancesMarkedForTermination() +
                        " marked for termination.");

                synchronizeAwsInstances();
                terminateMarkedInstances();
                doHealthCheck();
                getExecutedMetrics();
                getCPUUsage();

                // Observe the system status to decide what to do
                AutoScalerAction status = AutoScaler.getSystemStatus();

                if(status.getAction() == AutoScalerActionEnum.NO_ACTION) {
                    System.out.println("[Auto Scaler] No auto scaling action this round.");
                } else if(status.getAction() == AutoScalerActionEnum.DECREASE_FLEET) {
                    System.out.println("[Auto Scaler] Decreasing fleet power by terminating " + status.getCount() + " instances.");
                    AutoScaler.markForTermination(status.getCount());
                } else  if(status.getAction() == AutoScalerActionEnum.INCREASE_FLEET) {
                    System.out.println("[Auto Scaler] Increasing fleet power with " + status.getCount() + " new instances.");
                    AutoScaler.increaseFleet(status.getCount());
                }

            } catch(InterruptedException e) {
                System.out.println("[Auto Scaler] Thread interrupted.");
            } catch(Exception e) {
                System.out.println("[Auto Scaler] Exception occurred in auto-scaler thread: " + e.getMessage());
            }
        }
    }

    private static void terminateMarkedInstances() {
        // Check if there are any instances marked for termination without jobs, if so terminate them
        // this can happen in some buggy scenarios where instances start, they have no jobs and are marked
        // for termination. Never actually terminating.
        InstanceManager.terminateMarkedInstances();
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
            InstanceManager.queryExecutedMetrics();
        } else {
            if(System.currentTimeMillis() - startupTimestamp > Configs.EXECUTED_METRICS_FIRST_TIME_CHECK) {
                InstanceManager.queryExecutedMetrics();
                recentStartExecutedMetrics = false;
            }
        }
    }

    /**
     * Gets average CPU usage of EC2 Instance every 60 seconds
     */
    private static void getCPUUsage() {
        long currTime = System.currentTimeMillis();

        if((currTime - cpuUsageTimestamp) > Configs.CPU_USAGE_CHECK_TIME)
            AwsHandler.getCloudWatchCPUUsage();
    }

    /**
     * Sends health check request every 30 seconds
     */
    private static void doHealthCheck() {
        // Check the status of the VM's every 30 seconds
        if(System.currentTimeMillis() - lastLifeCheckTimestamp > Configs.HEALTH_CHECK_TIME) {
            InstanceManager.checkInstancesHealthStatus();
            lastLifeCheckTimestamp = System.currentTimeMillis();
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
            Set<Instance> desynchronizedInstances = InstanceManager.findDesynchronizedInstances(instances);

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
                        InstanceManager.addInstance(inst);
                    }
                } else {
                    System.out.println("[AutoScaler] Terminating de-synced remote EC2 instance that failed to answer the health check.");
                    AwsHandler.terminateEC2Instance(instanceId);
                }

            }
        }
    }

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
