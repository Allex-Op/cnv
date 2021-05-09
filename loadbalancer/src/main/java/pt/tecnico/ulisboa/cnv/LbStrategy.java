package pt.tecnico.ulisboa.cnv;

public class LbStrategy extends InstanceManager {

    /**
     *  It distributes the request according to the implemented
     *  algorithms.
     */
    public static byte[] distributeRequest(Job job, String query) {
        System.out.println("[Lb Strategy] Distributing a new request: " + query);
        EC2Instance ec2 = selectInstance(job);
        System.out.println("[Lb Strategy] Found an available instance to process the request: " + query);
        return ec2.executeRequest(job, query);
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
     * delay smaller and fast requests, but alas, it is what it is.
     *
     */
    private synchronized static EC2Instance selectInstance(Job job) {
        int waitingRounds = 0;
        EC2Instance inst = null;
        while(true) {
            // Some requests will exceed anyway the capacity of a VM, this request will be
            // looped forever and stop everything else if its not handled.
            if(waitingRounds == Configs.MAX_WAITING_ROUNDS || job.expectedCost > Configs.VM_PROCESSING_CAPACITY) {
                // But what if we reached max capacity vm's already? We can't allow to keep growing or an attacker
                // could spawn many vm's uncontrollably...
                System.out.println("[LbStrategy Part 1] A request that exceeds VM capacity, or that has been waiting for too long appeared...");
                if (InstanceManager.getInstancesSize() >= Configs.MAXIMUM_CAPACITY) {
                    System.out.println("[LbStrategy Part 2] The request can't create a new VM as max fleet size has been achieved, distributing randomly.");
                    // Can't do anything else except pray and spray. An unlucky random VM will be overwhelmed, but
                    // it's better than blocking the whole load balancer.

                    return InstanceManager.getInstance(0);
                } else {
                    System.out.println("[LbStrategy Part 2] The request will create a new VM as the current fleet size is smaller than the maximum configured.");

                    inst = new EC2Instance();
                    inst.startInstance();
                    InstanceManager.addInstance(inst);
                    return inst;
                }
            }

            inst = InstanceManager.searchInstanceWithEnoughResources(job);
            if(inst != null)
                return inst;

            inst = InstanceManager.searchInstanceWithJobAlmostFinished(job);
            if(inst != null)
                return inst;

            // No instances available, sleep for 1 second, there is no point in instantly checking for free instances.
            try {
                waitingRounds++;
                Thread.sleep(1000);
            } catch(InterruptedException e) {}
        }
    }


}
