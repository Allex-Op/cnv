package pt.tecnico.ulisboa.cnv;

public class LbStrategy extends InstanceManager {

    /**
     *  It distributes the request according to the implemented
     *  algorithms.
     */
    public static byte[] distributeRequest(Job job, String query) {
        EC2Instance ec2 = selectInstance(job);
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
        while(true) {
            // First search for an instance that has resources to answer the request
            for (EC2Instance instance : instances) {
                // If instance not above processing threshold
                if (instance.hasCapacityToProcess(job))
                    return instance;
            }

            // If there is no instance to answer the request, see if any instance is almost done completing a job
            for (EC2Instance instance : instances) {
                if (instance.checkIfAnyJobIsAlmostDone() + job.expectedCost < EC2Instance.PROCESSING_CAPACITY)
                    return instance;
            }

            // Sleep for 1 second, there is no point in instantly checking for free instances.
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {}
        }
    }


}
