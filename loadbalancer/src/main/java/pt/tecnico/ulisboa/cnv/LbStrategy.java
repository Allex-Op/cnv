package pt.tecnico.ulisboa.cnv;

public class LbStrategy extends InstanceManager {

    /**
     *  It distributes the request according to the implemented
     *  algorithms.
     */
    public static byte[] distributeRequest(Job job, String query) {
        EC2Instance ec2 = selectInstance(job);
        System.out.println("[Lb Strategy] Found an available instance to process the request: " + job.id);

        byte[] result = ec2.executeRequest(job, query);
        ec2.removeJob(job);

        return result;
    }

    /**
     * Selects an instance from the instances list
     * to distribute the request.
     *
     * If there is no instance currently available it will remain in a loop
     * until there is an instance available.
     *
     * As this method is synchronized, we guarantee that no new request
     * will steal the spot of the awaiting request. This can also backfire
     * as the request waiting can be way bigger than the new request, and
     * delay smaller and fast requests.
     *
     * It would be more efficient if this behavior didn't happen as very light requests can be slowed down
     * because of big requests waiting to be distributed.
     *
     * THe job cost is added here synchronously
     */
    private synchronized static EC2Instance selectInstance(Job job) {
        int waitingRounds = 0;
        EC2Instance inst = null;
        while(true) {
            // If the request waited nº waitingRounds or is bigger than a VM capacity
           inst = longWaitingRequest(job, waitingRounds);
            if(inst != null)
                break;

            // Instance with enough capacity to process the job
            inst = InstanceManager.searchInstanceWithEnoughResources(job);
            if(inst != null)
                break;

            // Instance which will have enough capacity after it completes soon a job
            inst = InstanceManager.searchInstanceWithJobAlmostFinished(job);
            if(inst != null)
                break;

            try {
                waitingRounds++;
                Thread.sleep(1000);
            } catch(InterruptedException ignored) {}
        }

        inst.addJob(job);
        return inst;
    }


    /**
     *  Some requests will exceed the capacity of a VM, those requests will be
     *  looped forever and stop everything else if its not handled.
     */
    private static EC2Instance longWaitingRequest(Job job, int waitingRounds) {
        EC2Instance inst = null;

        if(waitingRounds == Configs.MAX_WAITING_ROUNDS || job.expectedCost > Configs.VM_PROCESSING_CAPACITY) {
            System.out.println("[Lb Strategy] Request with id: " + job.id + " exceeds VM capacity or has been waiting for too long.");
            if (InstanceManager.getInstancesSize() >= Configs.MAXIMUM_FLEET_CAPACITY) {
                // Send request to the VM with lowest current capacity
                inst = InstanceManager.getInstanceWithLowestCapacity();
                return inst;
            } else {
                // If there is a vm without jobs use that one first
                inst = InstanceManager.searchInstanceWithoutJobs();
                if(inst != null)
                    return inst;

                // if there is no free vm create a new one for the big request
                System.out.println("[Lb Strategy] Creating new VM to process request id: " + job.id);
                inst = new EC2Instance();
                inst.startInstance();
                InstanceManager.addInstance(inst);
            }
        }

        return inst;
    }
}
