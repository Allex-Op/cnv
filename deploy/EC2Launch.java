package deploy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Iterator;
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
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.elasticloadbalancing.*;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerResult;


public class EC2Launch {
    static AmazonEC2 ec2;
    static AmazonElasticLoadBalancing elb;

    //TODO: Autoscaler e load balancer
    static AWSCredentials credentials = null;

    private static void init() throws Exception {
        // Vai tentar ler as credenciais localizadas em ~/.aws/credentials
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannon load credentials, make sure they exist.", e);
        }
        ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-2").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        elb = AmazonElasticLoadBalancingClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("us-east-2")
                .build();
    }

    public static void main(String[] args) throws Exception {
        init();
        //String instanceId = startInstance();
        //createLoadBalancer();
        //registerInstancesToLb();
        //Thread.sleep(60000);
        //terminateInstance(instanceId);
        terminateLoadBalancer();
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

    /**
     * Creates an ELB Load Balancer, which listens on port 80 and sends for port 8000
     * of the balanced vm's.
     *
     * TODO: It may need to configure the Security-Group and other stuff....
     */
    private static void createLoadBalancer() {
        System.out.println("Creating ELB Load Balancer...");

        DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();

        CreateLoadBalancerRequest lbRequest = new CreateLoadBalancerRequest();
        lbRequest.setLoadBalancerName("cnv-lb");
        List<Listener> listeners = new ArrayList<Listener>(1);
        listeners.add(new Listener("HTTP", 80, 8000));

        List<AvailabilityZone> avalZones = availabilityZonesResult.getAvailabilityZones();
        System.out.println("Availability zone used by LB:" + avalZones.get(0));
        lbRequest.withAvailabilityZones("us-east-2a");  // TODO: Hardcoded needs fix
        lbRequest.setListeners(listeners);

        CreateLoadBalancerResult lbResult = elb.createLoadBalancer(lbRequest);

    }

    /**
     * Registers instances that will be load balanced
     *
     * NOT TESTED YET IT WILL PROBABLY BLOW UP BECAUSE OF REGISTER.SETINSTANCES
     */
    private static void registerInstancesToLb() {
        System.out.println("Registering instances with load balancer...");
        List<Instance> instances = getInstances();

        String id;
        List instanceId = new ArrayList();
        List instanceIdString = new ArrayList();
        Iterator<Instance> iterator = instances.iterator();

        while (iterator.hasNext())
        {
            id=iterator.next().getInstanceId();
            instanceId.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(id));
            instanceIdString.add(id);
        }

        //register the instances to the balancer
        RegisterInstancesWithLoadBalancerRequest register = new RegisterInstancesWithLoadBalancerRequest();
        register.setLoadBalancerName("cnv-lab");
        register.setInstances(instanceId);
        RegisterInstancesWithLoadBalancerResult registerWithLoadBalancerResult = elb.registerInstancesWithLoadBalancer(register);
    }

    /**
     *  Obtains running EC2 instances in the cloud
     */
    private static List<Instance> getInstances() {
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        List<Instance> instances = new ArrayList<Instance>();

        for(Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }

        return instances;
    }

    /**
     * Terminate Load Balancer
     */
    private static void terminateLoadBalancer() {
        System.out.println("Terminating load balancer...");
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest().withLoadBalancerName("cnv-lb");
        DeleteLoadBalancerResult response = elb.deleteLoadBalancer(request);
    }

    /**
     * Creates an auto-scaler
     */
    private static void createAutoScaler() {
        return;
    }

    /**
     * Terminates auto-scaler
     */
    private static void terminateAutoScaler() {
        return;
    }

    /**
     * Create MSS
     *
     * Only mandatory for phase 2
     */
    private static void createMSS() {
        return;
    }

    /**
     * Terminate MSS
     *
     *  Only mandatory for phase 2
     */
    private static void terminateMSS() {
        return;
    }
}