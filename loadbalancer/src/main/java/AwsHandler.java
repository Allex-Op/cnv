import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

/**
 * Contains the information to interact
 * with the AWS API.
 */
public class AwsHandler {
    static AWSCredentials credentials = null;

    static AmazonEC2 ec2;
    static AmazonDynamoDB dynamoDBClient;

    public static void init() {
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
    }

    public static String[] createEC2Instance() {
        try {
            System.out.println("Starting instance...");

            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(Configs.AMI_ID)
                    .withInstanceType(Configs.INSTANCE_TYPE)
                    .withMinCount(1)
                    .withMaxCount(1)
                    .withKeyName(Configs.KEY_PAIR_NAME)
                    .withSecurityGroups(Configs.SECURITY_GROUP_NAME);


            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            Instance inst = runInstancesResult.getReservation().getInstances().get(0);
            String newInstanceId = inst.getInstanceId();
            String newInstancePublicIp = inst.getPublicIpAddress();

            System.out.println("Instance started with id: " + newInstanceId);
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
        System.out.println("Terminating instance with id: " + instanceId);
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        ec2.terminateInstances(termInstanceReq);
    }

    public static void writeToMss() {
        //TODO:
    }

    public static void readFromMss(RequestArguments args) {
        //TODO:
    }
}
