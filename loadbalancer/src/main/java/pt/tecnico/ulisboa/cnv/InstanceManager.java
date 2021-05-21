package pt.tecnico.ulisboa.cnv;

import com.amazonaws.services.ec2.model.Instance;
import pt.tecnico.ulisboa.cnv.AutoScaler.AutoScaler;
import pt.tecnico.ulisboa.cnv.AutoScaler.AutoScalerAction;
import pt.tecnico.ulisboa.cnv.AutoScaler.AutoScalerActionEnum;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    static final Object lock = new Object();

    /**
     * It checks if all the instances registered
     * are still alive. In case they are down the
     * specified object should be deleted.
     */
    public static void checkInstancesHealthStatus() {
        synchronized (lock) {
            for (EC2Instance instance : instances) {
                String url = Configs.healthCheckUrlBuild(instance.getInstanceIp());

                System.out.println("[Auto Scaler] Sending health check message to: " + url);
                boolean alive = sendHealthCheck(url);

                if (!alive) {
                    instance.incrementFailedHealthChecks();
                    if (instance.getFailedHealthChecks() > Configs.MAX_FAILED_HEALTH_CHECKS) {
                        System.out.println("[Auto Scaler] Instance removed for failing the maximum health checks.");

                        instances.remove(instance);
                    }
                } else {
                    instance.setFailedHealthChecks(0);
                }
            }
        }
    }

    /**
     *  Returns "true" if the instance answered with "alive"
     *  or "false" if no answer.
     */
    private static boolean sendHealthCheck(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return response.contains("alive");
        } catch (Exception e) {
            System.out.println("[Auto Scaler] Failed sending HTTP request (healthcheck). Instance unavailable.");
            return false;
        }
    }

    public static EC2Instance searchInstanceWithEnoughResources(Job job) {
        synchronized (lock) {
            // First search for an instance that has resources to answer the request
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination())
                    continue;

                // If instance not above processing threshold
                if (instance.hasCapacityToProcess(job))
                    return instance;
            }
            return null;
        }
    }

    public static EC2Instance searchInstanceWithJobAlmostFinished(Job job) {
        synchronized (lock) {
            // If there is no instance to answer the request, see if any instance is almost done completing a job
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination())
                    continue;

                if ( (instance.checkIfAnyJobIsAlmostDone() + job.expectedCost) < Configs.VM_PROCESSING_CAPACITY)
                    return instance;
            }
            return null;
        }
    }

    public static void terminateMarkedInstances() {
        synchronized (lock) {
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination() && instance.getCurrentCapacity() == 0) {
                    System.out.println("[Auto Scaler] Manually terminating instance without jobs that is marked for termination.");
                    instance.terminateInstance();
                    instances.remove(instance);
                }
            }
        }
    }

    public static void markForTermination(int marks) {
        synchronized (lock) {
            int currMarked = 0;
            int fleetSize = instances.size();

            for (EC2Instance instance : instances) {
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
    }

    public static int getInstancesSize() {
        synchronized (lock) {
            return instances.size();
        }
    }

    public static void addInstance(EC2Instance instance) {
        synchronized (lock) {
            instances.add(instance);
        }
    }

    public static void removeInstance(EC2Instance instance) {
        synchronized (lock) {
            instances.remove(instance);
        }
    }

    public static EC2Instance getInstance(int idx) {
        synchronized (lock) {
            return instances.get(idx);
        }
    }

    public static int getNumberOfInstancesMarkedForTermination() {
        synchronized (lock) {
            int count = 0;
            for (EC2Instance instance : instances) {
                if(instance.isMarkedForTermination())
                    count++;
            }

            return count;
        }
    }

    public static AutoScalerAction getInstancesSystemStatus() {
        synchronized (lock) {
            int fleetSize = instances.size();

            if(fleetSize < Configs.MINIMUM_FLEET_CAPACITY)
                return new AutoScalerAction(AutoScalerActionEnum.INCREASE_FLEET, Configs.MINIMUM_FLEET_CAPACITY - fleetSize);

            int instancesAboveThreshold = 0;
            int instancesBelowThreshold = 0;

            for (EC2Instance instance : instances) {
                if(instance.aboveProcessingThreshold()) {
                    // Before considering the VM as overwhelmed, check if it has any job that is almost complete
                    if(instance.checkIfAnyJobIsAlmostDone() < Configs.MAX_THRESHOLD_CAPACITY)
                        instancesAboveThreshold++;
                } else if(instance.belowProcessingThreshold()) {
                    instancesBelowThreshold++;
                }
            }

            return AutoScaler.decideAction(fleetSize, instancesAboveThreshold, instancesBelowThreshold);
        }
    }

    public static void queryExecutedMetrics() {
        synchronized (lock) {
            for (EC2Instance instance : instances) {
                instance.queryExecutedMetrics();
            }
        }
    }

    /**
     *  Finds from the running list of instances which currently
     *  exist in the context of the load balancer process (only known by AWS), if it doesn't
     *  exist is considered a de-synchronized instance.
     */
    public static Set<Instance> findDesynchronizedInstances(Set<Instance> runningInstances) {
        synchronized (lock) {
            Set<Instance> desynchronizedInstances = new HashSet<>();

            for (Instance instance : runningInstances) {
                // If the running instance doesn't exist then its considered de-synced
                if(!doesInstanceExist(instance)) {
                    desynchronizedInstances.add(instance);
                }
            }

            return desynchronizedInstances;
        }
    }

    /**
     *  Checks if this AWS instance exists in the LB
     *  process context. Only to be used by the above function.
     */
    private static boolean doesInstanceExist(Instance instance) {
        // Java synchronized blocks are reentrant, a deadlock won't
        // happen if a thread that owns this lock tries to enter here again.
        synchronized (lock) {
            String instanceId = instance.getInstanceId();

            for (EC2Instance ec2Instance : instances) {
                if(ec2Instance.getInstanceId().equals(instanceId))
                    return true;
            }

            return false;
        }
    }
}
