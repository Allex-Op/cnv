package pt.ulisboa.tecnico.cnv.deploy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

//ec2 instance
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.AvailabilityZone;

//cloudwatch
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.ComparisonOperator;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsResult;

import com.amazonaws.client.builder.AwsClientBuilder;

//Load balancer
import com.amazonaws.services.elasticloadbalancing.*;
import com.amazonaws.services.elasticloadbalancing.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;

//Autoscaler
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.*;
import com.amazonaws.services.autoscaling.model.*;

//DynamoDb
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.document.*;


import javax.management.Query;
import java.util.Iterator;



public class EC2Launch {
    static AmazonEC2 ec2;
    static AmazonElasticLoadBalancing elb;
    static AmazonAutoScaling scalerClient;
    static AmazonCloudWatch cw;
    static AWSCredentials credentials = null;
    static AmazonDynamoDB dynamoDBClient;

    // Snapshot image
    //static String AMI_ID = "ami-05d72852800cbf29e";   // Default EC2 Instance AMI
    public static String AMI_ID = "ami-0ad6abad76913ac20";     // EC2 Instance w/ Web Server on Reboot

    // Security configurations
    public static String SECURITY_GROUP_ID = "sg-0891d16f3a1e3bbcb";
    public static String SECURITY_GROUP_NAME = "cnv-test-monitoring";
    public static String KEY_PAIR_NAME = "cnv-portatil-key";

    // General configs
    public static String INSTANCE_TYPE = "t2.micro";
    public static String ZONE_NAME = "us-east-2a";
    public static String REGION_NAME = "us-east-2";

    // Load balancer configs
    public static String LB_NAME = "cnv-lb";
    public static String HEALTHCHECK_TYPE = "ELB";

    // AutoScaler configurations
    public static String LAUNCH_CONFIG_NAME  = "cnv-as-launch-config";
    public static String AUTO_SCALING_GROUP_NAME = "cnv-scaling-group";
    public static String ALARM_THRESHOLD_EXCEED = "alarmAS-Threshold-Exceeded";
    public static String ALARM_THRESHOLD_BELOW = "alarmAS-Threshold-Below-Limit";
    public static String POLICY_CREATE_INSTANCE = "createInstancePolicy";
    public static String POLICY_DELETE_INSTANCE = "removeInstancePolicy";
    public static String AWS_ACCOUNT_ID = "735932901659";


    // DynamoDB configurations
    public static String TABLE_NAME_DYNAMODB = "metrics-table";
    public static String PRIMARY_KEY_NAME_DYNAMODB = "identifier";
    public static String INDEX_DYNAMODB = "ArgsIndex";


    // Empty or replaced when another instance created
    static String instanceId = "i-0d4287d4622a5d469";

    private static void init() throws Exception {
        // Vai tentar ler as credenciais localizadas em ~/.aws/credentials
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannon load credentials, make sure they exist.", e);
        }

        // EC2 Instances client
        ec2 = AmazonEC2ClientBuilder
                .standard()
                .withRegion(REGION_NAME)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();

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

        // Cloudwatch client
        cw = AmazonCloudWatchClientBuilder
                .standard()
                .withRegion(REGION_NAME)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();

        // DynamoDB client
        dynamoDBClient = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(REGION_NAME)
                .build();
    }

    public static void main(String[] args) throws Exception {
        init();
        //createResources();
       // deleteResources();
        //startInstance();

        //createMSS();
        readFromMss();
    }

    /**
     * 1º Creates a Load Balancer
     * 2º Registers the new instances with the load balancer (OPTIONAL???)
     * 3º Creates Auto Scaler
     * 4º Creates scaling policies
     * 5º Creates alarms
     */
    private static void createResources() {
        createLoadBalancer();
        createAutoScaler();
        createScalingPolicies();
        createAlarms();
    }

    /**
     * Deletes all created resources
     */
    private static void deleteResources() {
        deleteScalingPolicies();
        deleteAlarms();
        terminateAutoScaler();
        terminateLoadBalancer();
    }

    /**
     * Creates and starts an EC2 instance
     * PS> The info below must be in the same region as connected with init().
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

        //System.out.println("Waiting a couple seconds so the new instance changes state to RUNNING...");
        //Thread.sleep(20000);
        //registerInstancesToLb();
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
        createLaunchConfiguration();

        System.out.println("Creating auto scaling group...");
        // Criar auto scaling group com a launch configuration criada, selecionar o LB que supervisiona
        CreateAutoScalingGroupRequest requestScalingGroup = new CreateAutoScalingGroupRequest().withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME)
                                            .withLaunchConfigurationName(LAUNCH_CONFIG_NAME)
                                            .withMinSize(2)
                                            .withMaxSize(4)
                                            .withAvailabilityZones(ZONE_NAME)
                                            .withLoadBalancerNames(LB_NAME)
                                            .withHealthCheckType(HEALTHCHECK_TYPE)
                                            .withHealthCheckGracePeriod(60);

        CreateAutoScalingGroupResult responseScalingGroup = scalerClient.createAutoScalingGroup(requestScalingGroup);
        return;
    }

    /**
     * Creates the alarms to be used by the scaling policy
     * so the auto-scaler can create or delete on demand new
     * instances.
     *
     * The alarms are not working as the metrics are not being gathered for the instances
     * for some reason...
     */
    private static void createAlarms() {
        try {
            /**
             * TODO: Entender o que dimension é e se esta relacionado
             * ao que é monitorizado...
             */

            Dimension dimension = new Dimension()
                    .withName("AutoScalingGroupName")
                    .withValue(AUTO_SCALING_GROUP_NAME);

            String arnResourceCreate = "arn:aws:autoscaling:"
                    + REGION_NAME + ":" + AWS_ACCOUNT_ID + ":scalingPolicy:policy-id"
                    + ":"+AUTO_SCALING_GROUP_NAME+":policyName/"+POLICY_CREATE_INSTANCE;

            System.out.println("Creating " + ALARM_THRESHOLD_EXCEED + " alarm.");
            PutMetricAlarmRequest requestHigherAlarm = new PutMetricAlarmRequest()
                    .withAlarmName(ALARM_THRESHOLD_EXCEED)
                    .withComparisonOperator(
                            ComparisonOperator.GreaterThanThreshold)
                    .withEvaluationPeriods(1)
                    .withMetricName("CPUUtilization")
                    .withNamespace("AWS/EC2")
                    .withPeriod(60)
                    .withStatistic(Statistic.Average)
                    .withThreshold(70.0)
                    .withActionsEnabled(false)
                    .withAlarmDescription("Alarm when server CPU utilization exceeds 70%")
                    .withUnit(StandardUnit.Seconds)
                    .withDimensions(dimension)
                    .withAlarmActions(arnResourceCreate);

            PutMetricAlarmResult responseHigherAlarm = cw.putMetricAlarm(requestHigherAlarm);


            System.out.println("Creating " + ALARM_THRESHOLD_BELOW + " alarm.");

            String arnResourceDelete = "arn:aws:autoscaling:"
                    + REGION_NAME + ":" + AWS_ACCOUNT_ID + ":scalingPolicy:policy-id"
                    + ":"+AUTO_SCALING_GROUP_NAME+":policyName/"+POLICY_DELETE_INSTANCE;

            PutMetricAlarmRequest requestLowerAlarm = new PutMetricAlarmRequest()
                    .withAlarmName(ALARM_THRESHOLD_BELOW)
                    .withComparisonOperator(
                            ComparisonOperator.LessThanThreshold)
                    .withEvaluationPeriods(1)
                    .withMetricName("CPUUtilization")
                    .withNamespace("AWS/EC2")
                    .withPeriod(60)
                    .withStatistic(Statistic.Average)
                    .withThreshold(10.0)
                    .withActionsEnabled(false)
                    .withAlarmDescription("Alarm when server CPU utilization goes below 10%")
                    .withUnit(StandardUnit.Seconds)
                    .withDimensions(dimension)
                    .withAlarmActions(arnResourceDelete);

            PutMetricAlarmResult responseLowerAlarm = cw.putMetricAlarm(requestLowerAlarm);

            System.out.println("Alarms created...");
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    /**
     * Creates the scaling policies to be used by the
     * auto-scaler group.
     * It used alarms created before hand.
     */
    private static void createScalingPolicies() {
        try {
            System.out.println("Creating scaling policies.");

            PutScalingPolicyRequest policyRequest = new PutScalingPolicyRequest()
                    .withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME)
                    .withPolicyName(POLICY_CREATE_INSTANCE)
                    .withAdjustmentType("ChangeInCapacity")
                    .withScalingAdjustment(1);

            PutScalingPolicyResult policyResponse = scalerClient.putScalingPolicy(policyRequest);


            PutScalingPolicyRequest policyRequestDelete = new PutScalingPolicyRequest()
                    .withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME)
                    .withPolicyName(POLICY_DELETE_INSTANCE)
                    .withAdjustmentType("ChangeInCapacity")
                    .withScalingAdjustment(-1);

            PutScalingPolicyResult policyResponseDelete = scalerClient.putScalingPolicy(policyRequestDelete);

            System.out.println("Policies created");
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    /**
     * Creates the launch configuration to be used by
     * the auto-scaler group. (image, max, min...)
     */
    private static void createLaunchConfiguration() {
        System.out.println("Creating launch configuration for auto scaler...");
        // Criar launch configuration
        CreateLaunchConfigurationRequest requestLaunchConfig = new CreateLaunchConfigurationRequest()
                .withLaunchConfigurationName(LAUNCH_CONFIG_NAME)
                .withImageId(AMI_ID)
                .withSecurityGroups(SECURITY_GROUP_ID)
                .withInstanceType(INSTANCE_TYPE);

        CreateLaunchConfigurationResult responseLaunchConfig = scalerClient.createLaunchConfiguration(requestLaunchConfig);
    }

    /**
     * Deletes the alarms used by the Auto-scaler group
     */
    private static void deleteAlarms() {
        System.out.println("Deleting alarms.");

        DeleteAlarmsRequest request = new DeleteAlarmsRequest()
                .withAlarmNames(ALARM_THRESHOLD_EXCEED, ALARM_THRESHOLD_BELOW);

        DeleteAlarmsResult response = cw.deleteAlarms(request);
    }

    /**
     * Deletes the scaling policies used by the auto-scaler
     */
    private static void deleteScalingPolicies() {
        System.out.println("Deleting scaling policies.");

        DeletePolicyRequest requestCreatePolicy = new DeletePolicyRequest()
                .withPolicyName(POLICY_CREATE_INSTANCE)
                .withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME);

        DeletePolicyResult responseCreatePolicy = scalerClient.deletePolicy(requestCreatePolicy);

        DeletePolicyRequest requestDeletePolicy = new DeletePolicyRequest()
                .withPolicyName(POLICY_DELETE_INSTANCE)
                .withAutoScalingGroupName(AUTO_SCALING_GROUP_NAME);

        DeletePolicyResult responseDeletePolicy = scalerClient.deletePolicy(requestDeletePolicy);

        System.out.println("Policies deleted...");
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
     * Create MSS with a table to store the metrics and inserts
     * mock data for the first requests received.
     */
    private static void createMSS() {
        System.out.println("Creating MSS.");

        try {
            DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);

            ArrayList<AttributeDefinition> attributeDefinitions = new ArrayList<AttributeDefinition>();

            attributeDefinitions.add(new AttributeDefinition()
                    .withAttributeName("identifier")
                    .withAttributeType("S")
            );

            attributeDefinitions.add(new AttributeDefinition()
                    .withAttributeName("strategy")
                    .withAttributeType("S")
            );

            ArrayList<KeySchemaElement> tableKeySchema = new ArrayList<KeySchemaElement>();

            tableKeySchema.add(new KeySchemaElement()
                    .withAttributeName("identifier")
                    .withKeyType(KeyType.HASH)
            );

            ProvisionedThroughput pt = new ProvisionedThroughput()
                    .withReadCapacityUnits((long) 5)
                    .withWriteCapacityUnits((long) 1);

            GlobalSecondaryIndex argsIndex = new GlobalSecondaryIndex()
                    .withIndexName(INDEX_DYNAMODB)
                    .withProvisionedThroughput(pt)
                    .withProjection(new Projection()
                            .withProjectionType(ProjectionType.ALL)
                    );

            ArrayList<KeySchemaElement> indexKeySchema = new ArrayList<KeySchemaElement>();
            indexKeySchema.add(new KeySchemaElement()
                    .withAttributeName("strategy")
                    .withKeyType(KeyType.HASH)
            );

            argsIndex.setKeySchema(indexKeySchema);

            CreateTableRequest createTableRequest = new CreateTableRequest()
                    .withTableName(TABLE_NAME_DYNAMODB)
                    .withProvisionedThroughput(pt)
                    .withAttributeDefinitions(attributeDefinitions)
                    .withKeySchema(tableKeySchema)
                    .withGlobalSecondaryIndexes(argsIndex);

            Table table = dynamoDB.createTable(createTableRequest);
            System.out.println("MSS Created, waiting 10 seconds before inserting data...");
            Thread.sleep(10000);
            insertInitialData();
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        }
        catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        catch (InterruptedException ie) {
            System.out.println("Something about the TableUtils.WaitUntilActive");
        }
    }

    /**
     * Function used to test the read from MSS
     */
    private static void readFromMss() {
        try {
            DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
            Table table = dynamoDB.getTable(TABLE_NAME_DYNAMODB);

            Index index = table.getIndex(INDEX_DYNAMODB);
            ItemCollection<QueryOutcome> items = null;

            QuerySpec querySpec = new QuerySpec();
            querySpec.withKeyConditionExpression("strategy = :v_strategy")
                    .withValueMap(new ValueMap()
                            .withString(":v_strategy", "GRID")
                    );

            items = index.query(querySpec);

            Iterator<Item> iterator = items.iterator();
            items = index.query(querySpec);

            while (iterator.hasNext()) {
                System.out.println(iterator.next().toJSONPretty());
            }

        } catch (AmazonServiceException ase) {
        System.out.println("Caught an AmazonServiceException, which means your request made it "
                + "to AWS, but was rejected with an error response for some reason.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("AWS Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }
        catch (AmazonClientException ace) {
        System.out.println("Caught an AmazonClientException, which means the client encountered "
                + "a serious internal problem while trying to communicate with AWS, "
                + "such as not being able to access the network.");
        System.out.println("Error Message: " + ace.getMessage());
    }
    }

    /**
     *  Inserts initial DB data
     */
    private static void insertInitialData() throws AmazonClientException {
        // Add an item
        // arguments: width, height, x0, x1, y0, y1, xS, yS, strategy, input, cost
        Map<String, AttributeValue> item = newItem("mock1", 512, 512, 0, 0, 128, 128, 10, 10, "GRID_SCAN", "SIMPLE_VORONOI_512_512_1.png", 30000000);
        PutItemRequest putItemRequest = new PutItemRequest(TABLE_NAME_DYNAMODB, item);
        PutItemResult putItemResult = dynamoDBClient.putItem(putItemRequest);
        System.out.println("Result: " + putItemResult);

        // Add another item
        item = newItem("mock2", 512, 512, 0, 0, 128, 128, 10, 10, "GRID_SCAN", "SIMPLE_VORONOI_512_512_1.png", 50000000);
        putItemRequest = new PutItemRequest(TABLE_NAME_DYNAMODB, item);
        putItemResult = dynamoDBClient.putItem(putItemRequest);
        System.out.println("Result: " + putItemResult);
    }

    /**
     *  Creates a new item
     *  arguments: width, height, x0, x1, y0, y1, xS, yS, strategy, input, cost
     */
    private static Map<String, AttributeValue> newItem(String randomId, int width, int height, int x0, int x1, int y0, int y1, int xS, int yS, String strategy, String input, long cost) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put(PRIMARY_KEY_NAME_DYNAMODB, new AttributeValue(randomId));
        item.put("width", new AttributeValue().withN(Integer.toString(width)));
        item.put("height", new AttributeValue().withN(Integer.toString(height)));
        item.put("x0", new AttributeValue().withN(Integer.toString(x0)));
        item.put("x1", new AttributeValue().withN(Integer.toString(x1)));
        item.put("y0", new AttributeValue().withN(Integer.toString(y0)));
        item.put("y1", new AttributeValue().withN(Integer.toString(y1)));
        item.put("xS", new AttributeValue().withN(Integer.toString(xS)));
        item.put("yS", new AttributeValue().withN(Integer.toString(yS)));
        item.put("strategy", new AttributeValue(strategy));
        item.put("input", new AttributeValue(input));
        item.put("cost", new AttributeValue().withN(Long.toString(cost)));

        return item;
    }
}