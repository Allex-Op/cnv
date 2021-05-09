package pt.tecnico.ulisboa.cnv.AutoScaler;
import pt.tecnico.ulisboa.cnv.InstanceManager;


public class AutoScalerThread extends Thread {
    @Override
    public void run() {
        System.out.println("[Auto Scaler] Auto-Scaler thread started.");
        autoScale();
    }

    static long lastLifeCheckTimestamp = 0;

    private static void autoScale() {
        while(true) {
            try {
                Thread.sleep(5000);
                System.out.println("[Auto Scaler] Auto-scaler woke up.");

                terminateMarkedInstances();
                doHealthCheck();
                getExecutedMetrics();

                AutoScalerAction status = AutoScaler.getSystemStatus();

                if(status.getAction() == AutoScalerActionEnum.NO_ACTION) {
                    System.out.println("[Auto Scaler] No auto scaling action this round.");
                    continue;
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
                System.out.println(e.getMessage());
                System.out.println("[Auto Scaler] Some exception occurred...");
            }
        }
    }

    private static void terminateMarkedInstances() {
        // Check if there are any instances marked for termination without jobs, if so terminate them
        // this can happen in some buggy scenarios where instances start, they have no jobs and are marked
        // for termination. Never actually terminating.
        InstanceManager.terminateMarkedInstances();
    }

    //TODO: Send request to web servers to gather information on executed metrics
    private static void getExecutedMetrics() { }

    private static void doHealthCheck() {
        // Check the status of the VM's every 1 minute
        if(System.currentTimeMillis() - lastLifeCheckTimestamp > 30000) {
            InstanceManager.checkInstancesHealthStatus();
            lastLifeCheckTimestamp = System.currentTimeMillis();
        }
    }
}
