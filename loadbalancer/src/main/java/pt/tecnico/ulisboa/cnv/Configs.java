package pt.tecnico.ulisboa.cnv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Configs {
    // Scanning strategies
    public final static String GRID_SCAN = "GRID_SCAN";
    public final static String GREEDY_RANGE_SCAN = "GREEDY_RANGE_SCAN";
    public final static String PROGRESSIVE_SCAN = "PROGRESSIVE_SCAN";

    // Metrics/Costs configurations
    public final static int instrCost = 1;
    public final static int branchCost = 2;
    public final static int newCost = 15;
    public final static int fldStore = 10;
    public final static int fldRead = 10;

    // Cost correction configurations
    public final static int VIEWPORT_AREA_64 = 64*64;
    public final static int VIEWPORT_AREA_128 = 128*128;
    public final static int VIEWPORT_AREA_256 = 256*256;
    public final static int VIEWPORT_AREA_512 = 512*512;
    public final static int VIEWPORT_AREA_1024 = 1024*1024;

    // AWS pt.tecnico.ulisboa.cnv.Configs - General
    public final static String AMI_ID = "ami-0c46b75a086aada68";     // EC2 Instance w/ Web Server on Reboot
    public final static String INSTANCE_TYPE = "t2.micro";
    public final static String ZONE_NAME = "us-east-2a";
    public final static String REGION_NAME = "us-east-2";

    // AWS pt.tecnico.ulisboa.cnv.Configs - DynamoDB
    public final static String TABLE_NAME_DYNAMODB = "metrics-table";
    public final static String PRIMARY_KEY_NAME_DYNAMODB = "identifier";
    public final static String INDEX_DYNAMODB = "ArgsIndex";

    // AWS pt.tecnico.ulisboa.cnv.Configs - Security
    public final static String SECURITY_GROUP_ID = "sg-0891d16f3a1e3bbcb";
    public final static String SECURITY_GROUP_NAME = "cnv-test-monitoring";
    public final static String KEY_PAIR_NAME = "cnv-portatil-key";


    // Auto-Scaler configs
    public final static int MINIMUM_FLEET_CAPACITY = 2;
    public final static int MAXIMUM_FLEET_CAPACITY = 6;
    public final static int MINIMUM_FREE_INSTANCES = 2; // At least 2 instances should be free at any moment (except if max fleet size)
    public final static double BELOW_PROCESSING_THRESHOLD = 0.10;
    public final static double ABOVE_PROCESSING_THRESHOLD = 0.85;

    // Auto-Scaler Cost correction settings based on CloudWatch cpu observations
    public final static double COST_CORRECTION_TOLERANCE = 3; // Number of times tolerated that the auto-scaler observes CPU usage above threshold on all instances
    public final static double COST_CORRECTION_CPU_BASE = 1.0;
    public static double COST_CORRECTION_CPU_CURRENT = COST_CORRECTION_CPU_BASE;  // Cost used to correct the cost of requests, increased by the auto-scaler if the CPU usage of the instances is constantly above threshold
    public final static double COST_CORRECTION_FACTOR_INCREASE = 1.5; // Factor of 50% increase on Cost correction applied every time TOLERANCE is exceeded
    public final static double COST_CORRECTION_FACTOR_DECREASE = 0.75; // The decrease factor of the cost if the treshold is not being surprassed, the cost won't go lower than the BASE value of 1.0

    public static void increaseCostCorrection() {
        COST_CORRECTION_CPU_CURRENT *= COST_CORRECTION_FACTOR_INCREASE;
    }

    public static void decreaseCostCorrection() {
        COST_CORRECTION_CPU_CURRENT *= COST_CORRECTION_FACTOR_DECREASE;

        // If the cost is below or equal keep it at the base
        if(COST_CORRECTION_CPU_CURRENT <= COST_CORRECTION_CPU_BASE)
            COST_CORRECTION_CPU_CURRENT = COST_CORRECTION_CPU_BASE;
    }


    // Job configs
    public final static double PERCENTAGE_OF_JOB_COMPLETED = 0.9;

    // Instance configs
    public static String urlBuild(String ip) {
        return "http://" + ip + ":8000/";
    }

    public static String healthCheckUrlBuild(String ip) {
        return urlBuild(ip) + "health";
    }

    public final static int PORT = 8000;
    public final static int MAX_FAILED_HEALTH_CHECKS = 3;
    public final static int MAX_WAITING_ROUNDS = 5;
    public final static long WAIT_TIME_BEFORE_INSTANCE_AVAILABLE = 1000 * 20;
    public final static long VM_PROCESSING_CAPACITY = 100000000;  // Total capacity, the value used should be MAX_THRESHOLD_CAPACITY
    public final static double MAX_THRESHOLD_CAPACITY = Configs.VM_PROCESSING_CAPACITY * Configs.ABOVE_PROCESSING_THRESHOLD;
    public final static double LIGHT_REQUEST_THRESHOLD = VM_PROCESSING_CAPACITY * 0.05;
    public final static int MAX_TRIES_SENDING_REQUEST = 3;    // Amount of times attempted at sending a request before giving up


    // Autoscaler timers
    public final static long DESYNCHRONIZED_TEST_TIME = 1000 * 300; // Every 300 seconds
    public final static long HEALTH_CHECK_TIME = 1000 * 30; // Every 30 seconds
    public final static long CPU_USAGE_CHECK_TIME = 1000 * 180; // Every 180 seconds
    public final static long EXECUTED_METRICS_FIRST_TIME_CHECK = 1000 * 30; // Executed 30 seconds after startup, but after that every 5s


    // LoadBalancer (this code) instance id
    public static String loadBalancerInstanceId = "BRRRAPPPP";   // Used to instruct the auto-scaler to not try to synchronize this instance as an EC2 web server instance

    public static String initLbInstanceId() {
        try {
            String url = "http://169.254.169.254/latest/meta-data/instance-id";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            byte[] result = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            String instanceId = new String(result);
            System.out.println("[Load Balancer] The instance id of the load balancer EC2 Instance is: " + instanceId);

            return instanceId;
        } catch(Exception e) {
            System.out.println("[Load Balancer] Wasn't able to obtain the instance id of the load balancer EC2 instance.");
            return "BRRAPPPP";
        }
    }
}
