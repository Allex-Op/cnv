package pt.tecnico.ulisboa.cnv.AutoScaler;
import pt.tecnico.ulisboa.cnv.InstanceManager;


public class AutoScalerThread extends Thread {
    @Override
    public void run() {
        System.out.println("[Auto Scaler] Auto-Scaler thread started.");
        startupTimestamp = System.currentTimeMillis();
        autoScale();
    }

    static long startupTimestamp = 0;
    static long lastLifeCheckTimestamp = 0;
    static boolean recentStart = false;

    private static void autoScale() {
        while(true) {
            try {
                Thread.sleep(5000);
                System.out.println("[Auto Scaler] Auto-scaler woke up, currently there are: " +
                        InstanceManager.getInstancesSize() + " total instances and " + InstanceManager.getNumberOfInstancesMarkedForTermination() +
                        " marked for termination.");

                terminateMarkedInstances();
                doHealthCheck();
                getExecutedMetrics();

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

    private static void getExecutedMetrics() {
        // Instructs each instance to query the executed metrics of certain requests that pass a threshold.
        // Not all requests are queried for their metrics because it would add unnecessary overhead for small
        // requests if there are many being processed.

        // We must wait some time for the system to stabilize after the start (instances starting, getting ip address, ...)
        // before querying for the executed metrics.
        if(!recentStart) {
            InstanceManager.queryExecutedMetrics();
        } else {
            if(System.currentTimeMillis() - startupTimestamp > 30000) {
                InstanceManager.queryExecutedMetrics();
                recentStart = false;
            }
        }
    }

    private static void doHealthCheck() {
        // Check the status of the VM's every 30 seconds
        if(System.currentTimeMillis() - lastLifeCheckTimestamp > 30000) {
            InstanceManager.checkInstancesHealthStatus();
            lastLifeCheckTimestamp = System.currentTimeMillis();
        }
    }
}
