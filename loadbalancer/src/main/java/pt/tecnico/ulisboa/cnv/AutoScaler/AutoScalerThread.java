package pt.tecnico.ulisboa.cnv.AutoScaler;
import pt.tecnico.ulisboa.cnv.InstanceManager;

public class AutoScalerThread extends Thread {
    @Override
    public void run() {
        System.out.println("[Auto Scaler] Auto-Scaler thread started.");
        autoScale();
    }

    private static void autoScale() {
        long lastLifeCheckTimestamp = 0;
        while(true) {
            try {
                Thread.sleep(5000);
                System.out.println("[Auto Scaler] Scaling time.");

                // Check the status of the VM's
                if(System.currentTimeMillis() - lastLifeCheckTimestamp > 60000) {
                    InstanceManager.checkInstancesHealthStatus();
                    lastLifeCheckTimestamp = System.currentTimeMillis();
                }

                //TODO: Send request to web servers to gather information on executed metrics


                AutoScalerAction status = AutoScaler.getSystemStatus();

                if(status.getAction() == AutoScalerActionEnum.NO_ACTION) {
                    System.out.println("[Auto Scaler] No auto scaling action this round.");
                    continue;
                }
                else if(status.getAction() == AutoScalerActionEnum.DECREASE_FLEET) {
                    System.out.println("[Auto Scaler] Decreasing fleet power.");
                    AutoScaler.markForTermination(status.getCount());
                } else  if(status.getAction() == AutoScalerActionEnum.INCREASE_FLEET) {
                    System.out.println("[Auto Scaler] Increasing fleet power.");
                    AutoScaler.increaseFleet(status.getCount());
                }

            } catch(InterruptedException e) {
                System.out.println("[Auto Scaler] Thread interrupted.");
            }
        }
    }
}
