package deploy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

public class EC2Launch {
    static AmazonEC2 ec2;

    //TODO: Autoscaler e load balancer

    private static void init() throws Exception {
        // Vai tentar ler as credenciais localizadas em ~/.aws/credentials
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannon load credentials, make sure they exist.", e);
        }
        ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-2").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

    public static void main(String[] args) throws Exception {
        init();
        String instanceId = startInstance();
        Thread.sleep(60000);
        terminateInstance(instanceId);
    }

    /**
     * Creates and starts an EC2 instance
     * PS> The info below must be in the same region as connected with init()
     */
    private static String startInstance() {
        try {
            System.out.println("Starting instance...");

            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId("ami-0ef81092d531b5196")
                    .withInstanceType("t2.micro")
                    .withMinCount(1)
                    .withMaxCount(1)
                    .withKeyName("cnv-monitor-teste")
                    .withSecurityGroups("cnv-test-monitoring");


            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();

            System.out.println("Instance started with id: " + newInstanceId);
            return newInstanceId;
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());

            return "";
        }
    }

    /**
     * Terminates an running instance
     */
    private static void terminateInstance(String instanceId) {
        System.out.println("Terminating instance with id: " + instanceId);
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
    }

    private static void createLoadBalancer() {
        return;
    }

    private static void terminateLoadBalancer() {
        return;
    }

    private static void createAutoScaler() {
        return;
    }

    private static void terminateAutoScaler() {
        return;
    }
}