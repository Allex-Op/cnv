package pt.tecnico.ulisboa.cnv.AutoScaler;

import pt.tecnico.ulisboa.cnv.Configs;
import pt.tecnico.ulisboa.cnv.EC2Instance;
import pt.tecnico.ulisboa.cnv.InstanceManager;

import java.util.List;

public class AutoScaler extends InstanceManager {
    /**
     * It increases the number of running web
     * servers that process requests.
     */
    protected static void increaseFleet(int count) {
        int fleetSize = InstanceManager.getInstancesSize();
        if(fleetSize >= Configs.MAXIMUM_FLEET_CAPACITY) {
            System.out.println("[Auto Scaler] Can't increase fleet power further. Reached max capacity.");
            return;
        }

        for (int i = 0; i < count; i++) {
            // Constraint check
            if(fleetSize < Configs.MAXIMUM_FLEET_CAPACITY) {
                System.out.println("[Auto Scaler] Added a new instance to the fleet.");
                EC2Instance inst = new EC2Instance();
                inst.startInstance();
                InstanceManager.addInstance(inst);
            }
        }
    }

    /**
     * Called by the auto-scaler after it decided
     * that it should cut the power of the system.
     *
     * To mark for termination it will check which instances
     * are below 25% capacity.
     */
    protected static void markForTermination(int marks) {
        int currMarked = 0;
        int fleetSize = InstanceManager.getInstancesSize();

        for (EC2Instance instance : instances.values()) {
            if(instance.belowProcessingThreshold()) {

                // Constraint check
                if(fleetSize <= Configs.MINIMUM_FLEET_CAPACITY) {
                    System.out.println("[Auto Scaler] Can't mark anymore instances for termination, as it would bring the number below the minimum configured.");
                    return;
                }

                // Only mark for termination if its not terminating already
                if(!instance.isMarkedForTermination()) {
                    instance.setMarkedForTermination(true);
                    currMarked++;
                    fleetSize--;
                    System.out.println("[Auto Scaler] Instance marked for termination.");
                }

                // It will mark X number of vm's for termination, this to avoid
                // terminating all vm's in case all are below the defined threshold
                if(currMarked == marks)
                    return;
            }
        }
    }


    /**
     *  For the auto scaler to decide if it should
     *  increase or decrease the fleet of vm's
     *  it must first observe the system status.
     *
     *  If the system is being overwhelmed then it must create
     *  more instances.
     *
     *  If the system has many vm's not doing anything then
     *  it must terminate some of them.
     *
     *  The 3 possible results are: NO_ACTION, INCREASE_FLEET, DECREASE_FLEET
     */
    protected static AutoScalerAction getSystemStatus() {
        int fleetSize = InstanceManager.getInstancesSize();

        if(fleetSize < Configs.MINIMUM_FLEET_CAPACITY)
            return new AutoScalerAction(AutoScalerActionEnum.INCREASE_FLEET, Configs.MINIMUM_FLEET_CAPACITY - fleetSize);

        int instancesAboveThreshold = 0;
        int instancesBelowThreshold = 0;

        for (EC2Instance instance : instances.values()) {
            if(instance.aboveProcessingThreshold()) {

                // Before considering the VM as overwhelmed, check if it has any job that is almost complete
                // only consider the instance as overwhelmed if the jobs that are about to conclude still wont
                // release enough capacity to be below the threshold capacity.
                if(instance.getCapacityAfterConcludingJobsFinish() > Configs.MAX_THRESHOLD_CAPACITY)
                    instancesAboveThreshold++;
            } else if(instance.belowProcessingThreshold()) {
                instancesBelowThreshold++;
            }
        }

        return AutoScaler.decideAction(fleetSize, instancesAboveThreshold, instancesBelowThreshold);
    }

    /**
     *  Based on the gathered information it will decide what action it should do.
     *  TODO:This code is kinda sketchy, maybe it should be revised in the future....
     */
    protected static AutoScalerAction decideAction(int fleetSize, int instancesAboveThreshold, int instancesBelowThreshold) {
        if(fleetSize == Configs.MINIMUM_FLEET_CAPACITY) {
            System.out.println("instances above threshold:" + instancesAboveThreshold);
            System.out.println("instances below threshold:" + instancesBelowThreshold);
            System.out.println("fleet size:" + fleetSize);

            // Only command the creation of a new VM if almost all vm's are above threshold
            if((fleetSize - instancesAboveThreshold) < Configs.MINIMUM_FREE_INSTANCES) {
                return new AutoScalerAction(AutoScalerActionEnum.INCREASE_FLEET, 1);
            } else {
                // The below threshold shouldn't be counted in the case of minimium_fleet_capacity
                // as we dont want to go below that.
                return new AutoScalerAction(AutoScalerActionEnum.NO_ACTION);
            }

        } else if (fleetSize > Configs.MINIMUM_FLEET_CAPACITY) {
            // The auto-scaler should try having at least 2 instances free in-case a burst of request comes
            // not having to wait or at least avoiding the delay of startup of a new instance.
            if (instancesBelowThreshold > Configs.MINIMUM_FREE_INSTANCES) {
                return new AutoScalerAction(AutoScalerActionEnum.DECREASE_FLEET, 1);
            } else if ((fleetSize - instancesAboveThreshold) < Configs.MINIMUM_FREE_INSTANCES) {
                return new AutoScalerAction(AutoScalerActionEnum.INCREASE_FLEET, 1);
            }
        }

        return new AutoScalerAction(AutoScalerActionEnum.NO_ACTION);
    }
}
