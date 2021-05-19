package pt.tecnico.ulisboa.cnv;

public class Configs {
    // Scanning strategies
    public static String GRID_SCAN = "GRID_SCAN";
    public static String GREEDY_RANGE_SCAN = "GREEDY_RANGE_SCAN";
    public static String PROGRESSIVE_SCAN = "PROGRESSIVE_SCAN";

    // Metrics/Costs configurations
    public static int instrCost = 1;
    public static int branchCost = 2;
    public static int newCost = 15;
    public static int fldStore = 10;
    public static int fldRead = 10;

    // Cost correction configurations
    public static int VIEWPORT_AREA_64 = 64*64;
    public static int VIEWPORT_AREA_128 = 128*128;
    public static int VIEWPORT_AREA_256 = 256*256;
    public static int VIEWPORT_AREA_512 = 512*512;
    public static int VIEWPORT_AREA_1024 = 1024*1024;

    // AWS pt.tecnico.ulisboa.cnv.Configs - General
    public static String AMI_ID = "ami-0ad6abad76913ac20";     // EC2 Instance w/ Web Server on Reboot
    public static String INSTANCE_TYPE = "t2.micro";
    public static String ZONE_NAME = "us-east-2a";
    public static String REGION_NAME = "us-east-2";

    // AWS pt.tecnico.ulisboa.cnv.Configs - DynamoDB
    public static String TABLE_NAME_DYNAMODB = "metrics-table";
    public static String PRIMARY_KEY_NAME_DYNAMODB = "identifier";
    public static final String INDEX_DYNAMODB = "ArgsIndex";

    // AWS pt.tecnico.ulisboa.cnv.Configs - Security
    public static String SECURITY_GROUP_ID = "sg-0891d16f3a1e3bbcb";
    public static String SECURITY_GROUP_NAME = "cnv-test-monitoring";
    public static String KEY_PAIR_NAME = "cnv-portatil-key";


    // Auto-Scaler configs
    public static int MINIMUM_CAPACITY = 2;
    public static int MAXIMUM_CAPACITY = 6;
    public static double BELOW_PROCESSING_THRESHOLD = 0.25;
    public static double ABOVE_PROCESSING_THRESHOLD = 0.85;

    // Job configs
    public static double PERCENTAGE_OF_JOB_COMPLETED = 0.75;

    // Instance configs
    public static String urlBuild(String ip) {
        return "http://" + ip + ":8000/";
    }
    public static int PORT = 8000;
    public static int MAX_FAILED_HEALTH_CHECKS = 3;
    public static int MAX_WAITING_ROUNDS = 5;
    public static long WAIT_TIME_BEFORE_INSTANCE_AVAILABLE = 20000;
    public static long VM_PROCESSING_CAPACITY = 100000000;  // Total capacity, the value used should be MAX_THRESHOLD_CAPACITY
    public static double MAX_THRESHOLD_CAPACITY = Configs.VM_PROCESSING_CAPACITY * Configs.ABOVE_PROCESSING_THRESHOLD;
    public static double LIGHT_REQUEST_THRESHOLD = VM_PROCESSING_CAPACITY * 0.05;


}
