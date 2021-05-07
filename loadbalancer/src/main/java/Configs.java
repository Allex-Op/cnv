public class Configs {
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

    // AWS Configs - General
    public static String AMI_ID = "ami-0ad6abad76913ac20";     // EC2 Instance w/ Web Server on Reboot
    public static String INSTANCE_TYPE = "t2.micro";
    public static String ZONE_NAME = "us-east-2a";
    public static String REGION_NAME = "us-east-2";

    // AWS Configs - DynamoDB
    public static String TABLE_NAME_DYNAMODB = "metrics-table";
    public static String PRIMARY_KEY_NAME_DYNAMODB = "identifier";

    // AWS Configs - Security
    public static String SECURITY_GROUP_ID = "sg-0891d16f3a1e3bbcb";
    public static String SECURITY_GROUP_NAME = "cnv-test-monitoring";
    public static String KEY_PAIR_NAME = "cnv-portatil-key";
}
