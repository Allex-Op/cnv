package pt.tecnico.ulisboa.cnv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.ulisboa.cnv.model.DbEntry;

import java.util.*;

import static pt.tecnico.ulisboa.cnv.InstanceManager.instances;

/**
 * Contains the information to interact
 * with the AWS API.
 */
public class AwsHandler {
    static AWSCredentials credentials = null;
    static AmazonEC2 ec2;
    static AmazonDynamoDB dynamoDBClient;
    static AmazonCloudWatch cloudWatch;

    public static void init() {
        System.out.println("[AwsHandler] Initializing credentials...");
        // Credentials must be at ~/.aws/credentials
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannot load credentials, make sure they exist.", e);
        }

        // EC2 Instances client
        ec2 = AmazonEC2ClientBuilder
                .standard()
                .withRegion(Configs.REGION_NAME)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();

        // DynamoDB client
        dynamoDBClient = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Configs.REGION_NAME)
                .build();

        // Cloudwatch client
        cloudWatch = AmazonCloudWatchClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Configs.REGION_NAME)
                .build();
    }

    public static String[] createEC2Instance() {
        try {
            System.out.println("[AwsHandler] Starting EC2 instance...");

            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(Configs.AMI_ID)
                    .withInstanceType(Configs.INSTANCE_TYPE)
                    .withMinCount(1)
                    .withMaxCount(1)
                    .withKeyName(Configs.KEY_PAIR_NAME)
                    .withSecurityGroups(Configs.SECURITY_GROUP_NAME);


            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            Instance inst = runInstancesResult.getReservation().getInstances().get(0);

            String newInstancePublicIp = inst.getPublicIpAddress();
            String newInstanceId = inst.getInstanceId();

            System.out.println("[AwsHandler] Instance started with id: " + newInstanceId);
            return new String[] { newInstanceId, newInstancePublicIp };
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());

            return new String[] {};
        }
    }

    public static void terminateEC2Instance(String instanceId) {
        System.out.println("[AwsHandler] Terminating instance with id: " + instanceId);
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
    }

    public static String getPublicIpOfInstance(String instanceId) {
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        DescribeInstancesResult response = ec2.describeInstances(request);

        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                if(instance.getInstanceId().equals(instanceId)) {
                    return instance.getPublicIpAddress();
                }
            }
        }

        return "";
    }

    /**
     *  Reads entries from the MSS, used to find the most similar
     *  request to attribute a cost. Currently queried by the strategy argument
     *  to avoid bringing in other unnecessary information. Another strategy could be
     *  periodically reading all information.
     *
     *  Alternative to current strategy would be using "batchGetItem" but its kinda weird and has a limit.
     */
    public static List<DbEntry> readFromMss(String strategy) {
        try {
            System.out.println("[AwsHandler] Reading from MSS entries with strategy: " + strategy);

            DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
            Table table = dynamoDB.getTable(Configs.TABLE_NAME_DYNAMODB);

            Index index = table.getIndex(Configs.INDEX_DYNAMODB);
            ItemCollection<QueryOutcome> items = null;

            QuerySpec querySpec = new QuerySpec();
            querySpec.withKeyConditionExpression("strategy = :v_strategy")
                    .withValueMap(new ValueMap()
                            .withString(":v_strategy", strategy)
                    );

            items = index.query(querySpec);

            Iterator<Item> iterator = items.iterator();
            items = index.query(querySpec);

            List<DbEntry> dbEntries = new ArrayList<>();
            while (iterator.hasNext()) {
                String dbEntryJson = iterator.next().toJSONPretty();
                ObjectMapper mapper = new ObjectMapper();
                DbEntry entry = mapper.readValue(dbEntryJson, DbEntry.class);
                dbEntries.add(entry);
            }

            System.out.println("[AwsHandler] Retrieved " + dbEntries.size() + " entries from the MSS.");
            return dbEntries;
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
        catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return new ArrayList<DbEntry>();
    }

    /**
     *  Obtains the CPU Usage for all running instances
     *  calculated by the average on 60 seconds.
     * @return
     */
    public static List<String> getCloudWatchCPUUsage() {
        long offsetInMilliseconds = 1000 * 60 * 10;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");

        List<Dimension> dims = new ArrayList<>();
        dims.add(instanceDimension);

        List<String> instancesAboveThreshold = new ArrayList<>();

        for (EC2Instance instance : instances.values()) {
            String name = instance.getInstanceId();
            instanceDimension.setValue(name);

            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                    .withNamespace("AWS/EC2")
                    .withPeriod(60)
                    .withMetricName("CPUUtilization")
                    .withStatistics("Average")
                    .withDimensions(instanceDimension)
                    .withEndTime(new Date());

            GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);

            List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
            for (Datapoint datapoint : datapoints) {
                double average = datapoint.getAverage();
                System.out.println("[AwsHandler] CPU Utilization Average for instance " + name + " : " + average);

                // If the instance processing percentage is above the defined threshold note that
                // and return it to the auto-scaler.
                if(average > Configs.ABOVE_PROCESSING_THRESHOLD)
                    instancesAboveThreshold.add(name);
            }
        }

        return instancesAboveThreshold;
    }

    /**
     *  Get all instances
     */
    private static Set<Instance> getInstances() {
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesResult.getReservations();
        Set<Instance> instances = new HashSet<Instance>();

        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }

        return instances;
    }

    /**
     * Get running instances
     */
    public static Set<Instance> getRunningInstances() {
        Set<Instance> instances = getInstances();
        Set<Instance> runningInstances = new HashSet<>();

        for (Instance instance : instances) {
            String state = instance.getState().getName();

            if(state.equals("running")) {
                runningInstances.add(instance);
            }
        }

        return runningInstances;
    }
}
