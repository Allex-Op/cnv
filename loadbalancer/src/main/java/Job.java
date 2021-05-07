import java.util.UUID;

/**
 * A job describes a request that is currently (or was) being processed.
 * Each EC2 Computing Instance can have multiple jobs running according to their
 * capacity.
 */
public class Job {
    // Identifies the job. The load balancer will keep track of this id.
    // This ID will also de used to delete this job from the running jobs in the
    // EC2 Instance after it receives the answer from the web server.
    // The web server answer must include this id. (or not??? to be defined TODO)
    String id;

    // The expected cost of processing this request with cost correction applied.
    // This base value is obtained from the MSS.
    long expectedCost;

    // Arguments associated with the job
    RequestArguments arguments;

    // Metrics executed of the current job
    ExecutedMetrics metrics = new ExecutedMetrics();

    public Job() {}

    public Job(RequestArguments arguments) {
        this.arguments = arguments;

        UUID uuid = UUID.randomUUID();
        this.id = uuid.toString();

        expectedCost = getCostOfMostSimilarJob();
        correctCost(arguments.calculateViewPort());
    }

    /**
     *  It will search for the most similar request
     *  that was already processed and take its cost.
     */
    private long getCostOfMostSimilarJob() {
        AwsHandler.readFromMss(arguments);
        return 0;
    }

    /**
     *  Returns true or false depending how much
     *  of the expected metrics were already executed.
     *
     *  If its past 75% of the metrics to execute it returns true, otherwise false.
     */
    public boolean isJobAlmostDone() {
        long executedCost = getExecutedCost();
        double completedPercentage = ((double) executedCost) / ((double) expectedCost);

        return completedPercentage > 0.75;
    }

    /**
     * Updates the current executed metrics.
     *
     * Parameter metrics of this function is the response
     * from the web server on the current request.
     */
    public void updateExecutedMetrics(ExecutedMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     *  Returns the "processed" cost to the moment
     *  of the current job.
     */
    private long getExecutedCost() {
        return metrics.calculateCurrentCost();
    }

    /**
     * It uses the defined linear regression equations
     * to correct the cost of a request, as different
     * requests, even if similar to the arguments in the MSS, may have
     * critical parameters that increase its real value.
     *
     * Currently, we only do corrections on the viewport parameter.
     */
    private void correctCost(long viewport) {
        // No correction needed for viewport argument when the value is one of values below, as there are already
        // default values in the MSS for them.
        if(viewport == Configs.VIEWPORT_AREA_256 || viewport == Configs.VIEWPORT_AREA_512 || viewport == Configs.VIEWPORT_AREA_1024)
            return;

        double deviation = 0;
        if(arguments.getStrategy().contains("grid")) {

            if(viewport > Configs.VIEWPORT_AREA_64 && viewport < Configs.VIEWPORT_AREA_256) {
                deviation = 0.0003790 * viewport + 0.2667;
            } else if(viewport > Configs.VIEWPORT_AREA_256) {
                deviation = 0.0003815 * viewport;
            }

        } else {
            if(viewport > Configs.VIEWPORT_AREA_512) {
                deviation = 0.0001256 * viewport - 31.67;
            }
        }

        expectedCost = (long) Math.floor(expectedCost * (1 + deviation));
    }

}
