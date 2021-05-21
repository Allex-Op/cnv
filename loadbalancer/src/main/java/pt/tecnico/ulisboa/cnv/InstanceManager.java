package pt.tecnico.ulisboa.cnv;

import com.amazonaws.services.ec2.model.Instance;

import java.util.*;

/**
 * Class deals with the concurrent management of the
 * instances.
 */
public class InstanceManager {
    // Instances list is an extremely concurred structure by the auto-scaler
    // and every new request that comes and need distribution, therefore it needs to
    // have its access controlled.
    private static List<EC2Instance> instances = new ArrayList<>();

    // Lock used to synchronize access to the instances list.
    public static final Object instancesLock = new Object();

    public static List<EC2Instance> getInstances() {
        return instances;
    }

    public static int getInstancesSize() {
        synchronized (instancesLock) {
            return instances.size();
        }
    }

    public static void addInstance(EC2Instance instance) {
        synchronized (instancesLock) {
            instances.add(instance);
        }
    }

    public static void removeInstance(EC2Instance instance) {
        synchronized (instancesLock) {
            instances.remove(instance);
        }
    }

    public static EC2Instance getInstance(int idx) {
        synchronized (instancesLock) {
            return instances.get(idx);
        }
    }

    public static EC2Instance searchInstanceWithoutJobs() {
        synchronized (instancesLock) {
            for (EC2Instance instance : instances) {
                if(instance.getNumberOfRequests() == 0)
                    return instance;
            }
            return null;
        }
    }

    /**
     *  Searches for an instance that has enought capacity process a @Job
     */
    public static EC2Instance searchInstanceWithEnoughResources(Job job) {
        synchronized (instancesLock) {
            // First search for an instance that has resources to answer the request
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination())
                    continue;

                // If instance not above processing threshold
                if (instance.hasCapacityToProcess(job)) {
                    System.out.println("[Instance Manager] Found an instance with enough resources to process Job: " + job.id);
                    return instance;
                }
            }
            return null;
        }
    }

    /**
     *  Searches for an instance that is soon going to have enough resources
     *  to process a @Job
     */
    public static EC2Instance searchInstanceWithJobAlmostFinished(Job job) {
        synchronized (instancesLock) {
            // If there is no instance to answer the request, see if any instance is almost done completing a job
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination())
                    continue;

                if ( (instance.checkIfAnyJobIsAlmostDone() + job.expectedCost) < Configs.VM_PROCESSING_CAPACITY) {
                    System.out.println("[Instance Manager] Found instance: " + instance.getInstanceId() +", that will soon have enough resources to process job: " + job.id);
                    return instance;
                }
            }
            return null;
        }
    }

    /**
     *  Used by the Auto-Scaler to find the number
     *  of instances that are going to terminate.
     */
    public static int getNumberOfInstancesMarkedForTermination() {
        synchronized (instancesLock) {
            int count = 0;
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination())
                    count++;
            }

            return count;
        }
    }

    /**
     *  Checks if this AWS instance exists in the LB
     *  process context. Only to be used by the above function.
     */
    public static boolean doesInstanceExist(Instance instance) {
        // Java synchronized blocks are reentrant, a deadlock won't
        // happen if a thread that owns this lock tries to enter here again.
        synchronized (instancesLock) {
            String instanceId = instance.getInstanceId();

            for (EC2Instance ec2Instance : instances) {
                if(ec2Instance.getInstanceId().equals(instanceId))
                    return true;
            }

            return false;
        }
    }

    /**
     *  Returns the instance with lowest capacity, used in the unfortunate case
     *  of not being able to create more VM's.
     */
    protected static EC2Instance getInstanceWithLowestCapacity() {
        synchronized (instancesLock) {
            EC2Instance lowest = instances.get(0);
            for (EC2Instance instance : instances) {
                if(instance.getCurrentCapacity() < lowest.getCurrentCapacity())
                    lowest = instance;
            }

            return lowest;
        }
    }
}
