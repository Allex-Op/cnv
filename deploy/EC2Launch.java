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

import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.client.builder.AwsClientBuilder;

import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.elasticloadbalancing.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.*;
import com.amazonaws.services.elasticloadbalancing.*;

public class EC2Launch {
    static AmazonEC2 ec2;
    static AmazonElasticLoadBalancing elb;
    static AmazonAutoScaling scalerClient;
    static AWSCredentials credentials = null;

    static String AMI_ID = "ami-043f66b7f8406a2fb";
    static String SECURITY_GROUP_ID = "sg-0891d16f3a1e3bbcb";
    static String SECURITY_GROUP_NAME = "cnv-test-monitoring";
    static String KEY_PAIR_NAME = "cnv-monitor-teste";
    static String INSTANCE_TYPE = "t2.micro";
    static String ZONE_NAME = "us-east-2a";
    static String REGION_NAME = "us-east-2";
    static String LB_NAME = "cnv-lb";
    static String LAUNCH_CONFIG_NAME  = "cnv-as-launch-config";
    static String AUTO_SCALING_GROUP_NAME = "cnv-scaling-group";


    private static void init() throws Exception {
        // Vai tentar ler as credenciais localizadas em ~/.aws/credentials
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannon load credentials, make sure they exist.", e);
        }

        // EC2 Instances client
        ec2 = AmazonEC2ClientBuilder.standard().withRegion(REGION_NAME).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

        // Load balancer client
        elb = AmazonElasticLoadBalancingClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(REGION_NAME)
                .build();

        // Auto scaler client
        scalerClient = AmazonAutoScalingClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(REGION_NAME)
                .build();
    }

    public static void main(String[] args) throws Exception {
        init();
        String instanceId = startInstance();
        System.out.println("Waiting a couple seconds so the new instance changes state to RUNNING...");
        Thread.sleep(20000);

        createLoadBalancer();
        registerInstancesToLb();

        System.out.println("Pause time, observe whats happening....");
        Thread.sleep(120000);
        System.out.println("Pause time over, terminating resources...");

        terminateInstance(instanceId);
        //createAutoScaler();

        //terminateAutoScaler();
        terminateLoadBalancer();
    }

    /**
     * Creates and starts an EC2 instance
     * PS> The info below must be in the same region as connected with init()
     *
     * BTW currently a instância n esta a escolher uma zona para spawnar "eu-east-2b,2c...", o load balancer
     * so funciona para o eu-east-2b, configurar...
     */
    private static String startInstance() {
        try {
            System.out.println("Starting instance...");

            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(AMI_ID)
                    .withInstanceType(INSTANCE_TYPE)
                    .withMinCount(1)
                    .withMaxCount(1)
                    .withKeyName(KEY_PAIR_NAME)
                    .withSecurityGroups(SECURITY_GROUP_NAME);


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
        lbRequest.setLoadBalancerName(LB_NAME);
        List<Listener> listeners = new ArrayList<Listener>(1);
        listeners.add(new Listener("HTTP", 80, 8000));

        List<AvailabilityZone> avalZones = availabilityZonesResult.getAvailabilityZones();
        lbRequest.withAvailabilityZones(ZONE_NAME);  // TODO: Hardcoded needs fix
        lbRequest.setListeners(listeners);

        CreateLoadBalancerResult lbResult = elb.createLoadBalancer(lbRequest);
        System.out.println("ELB Load Balancer created...");
    }

    /**
     * Registers instances that will be load balanced
     *
     * If there are terminated or shutdown instances, this code will throw exception
     * as its not possible to register a terminated instance.
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
            Instance inst = iterator.next();
            String instanceState = inst.getState().getName();
            id=inst.getInstanceId();
            System.out.println("Found instance with id: " + id + " and state: "+ instanceState +" , add to register list...");

            if(instanceState.equals("running")) {
                System.out.println("Going to register instance with id:" + id + " as it was found on running state...");
                instanceId.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(id));
                instanceIdString.add(id);
            }
        }

        try {
            System.out.println("Sending register with LB request...");
            //register the instances to the balancer
            RegisterInstancesWithLoadBalancerRequest register = new RegisterInstancesWithLoadBalancerRequest();
            register.setLoadBalancerName(LB_NAME);
            register.setInstances(instanceId);
            RegisterInstancesWithLoadBalancerResult registerWithLoadBalancerResult = elb.registerInstancesWithLoadBalancer(register);
        } catch(AmazonServiceException ase) {
            System.out.println("A few or all instances, were not able to be registered");
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
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
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest().withLoadBalancerName(LB_NAME);
        DeleteLoadBalancerResult response = elb.deleteLoadBalancer(request);
    }

    /**
     * Creates an auto-scaler
     */
    private static void createAutoScaler() {
        System.out.println("Creating launch configuration for auto scaler...");
        // Criar launch configuration
        CreateLaunchConfigurationRequest requestLaunchConfig = new CreateLaunchConfigurationRequest()
                                            .withLaunchConfigurationName(LAUNCH_CONFIG_NAME)
                                            .withImageId(AMI_ID)
                                            .withSecurityGroups(SECURITY_GROUP_ID)
                                            .withInstanceType(INSTANCE_TYPE);

        CreateLaunchConfigurationResult responseLaunchCOnfig = scalerClient.createLaunchConfiguration(requestLaunchConfig);

        System.out.println("Creating auto scaling group...");
        // Criar auto scaling group com a launch configuration criada, selecionar o LB que supervisiona
        CreateAutoScalingGroupRequest requestScalingGroup = new CreateAutoScalingGroupRequest().withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME)
                                            .withLaunchConfigurationName(LAUNCH_CONFIG_NAME)
                                            .withMinSize(1)
                                            .withMaxSize(3)
                                            .withAvailabilityZones(ZONE_NAME)
                                            .withLoadBalancerNames(LB_NAME); //.withHealthCheckType("ELB").withHealthCheckGracePeriod(120);

        CreateAutoScalingGroupResult responseScalingGroup = scalerClient.createAutoScalingGroup(requestScalingGroup);
        return;
    }

    /**
     * Terminates auto-scaler
     */
    private static void terminateAutoScaler() {
        System.out.println("Deleting auto scaling group...");

        // Apagar auto scaling group
        DeleteAutoScalingGroupRequest requestDeleteScalingGroup = new DeleteAutoScalingGroupRequest()
                .withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME)
                .withForceDelete(true);

        DeleteAutoScalingGroupResult responseDelScalingGroup = scalerClient.deleteAutoScalingGroup(requestDeleteScalingGroup);

        System.out.println("Deleting launch configurations for auto scaling...");
        // Apagar launch configuration
        DeleteLaunchConfigurationRequest requestDeleteLaunchConfig = new DeleteLaunchConfigurationRequest().withLaunchConfigurationName(LAUNCH_CONFIG_NAME);
        DeleteLaunchConfigurationResult responseDelLaunchConfig = scalerClient.deleteLaunchConfiguration(requestDeleteLaunchConfig);

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