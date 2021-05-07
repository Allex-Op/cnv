/**
 * Class contains information about the currently executed metrics.
 * Its also used as the response from the query sent by the LB to the Web Servers.
 */
public class ExecutedMetrics {
    long executedInstructions = 0;
    long executedBranchCheck = 0;
    long executedNew = 0;
    long executedMemoryRead = 0;
    long executedMemoryWrite = 0;


    public ExecutedMetrics() { }

    /**
     *  Calculates the cost of the current job at the CURRENT MOMENT.
     *  The load balancer will send periodically requests to the
     *  web servers to obtain information on what was processed of said requests.
     */
    public long calculateCurrentCost() {
        return executedInstructions * Configs.instrCost +
                executedBranchCheck * Configs.branchCost +
                executedNew * Configs.newCost +
                executedMemoryRead * Configs.fldRead +
                executedMemoryWrite * Configs.fldStore;
    }


    public long getExecutedBranchCheck() {
        return executedBranchCheck;
    }

    public long getExecutedInstructions() {
        return executedInstructions;
    }

    public long getExecutedMemoryRead() {
        return executedMemoryRead;
    }

    public long getExecutedMemoryWrite() {
        return executedMemoryWrite;
    }

    public long getExecutedNew() {
        return executedNew;
    }

    public void setExecutedBranchCheck(long executedBranchCheck) {
        this.executedBranchCheck = executedBranchCheck;
    }

    public void setExecutedInstructions(long executedInstructions) {
        this.executedInstructions = executedInstructions;
    }

    public void setExecutedMemoryRead(long executedMemoryRead) {
        this.executedMemoryRead = executedMemoryRead;
    }

    public void setExecutedMemoryWrite(long executedMemoryWrite) {
        this.executedMemoryWrite = executedMemoryWrite;
    }

    public void setExecutedNew(long executedNew) {
        this.executedNew = executedNew;
    }
}
