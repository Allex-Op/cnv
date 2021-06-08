package pt.tecnico.ulisboa.cnv;

import pt.tecnico.ulisboa.cnv.AutoScaler.AutoScalerThread;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LoadBalancerApplication {
    public static void main(String[] args) {
        String SERVER = "0.0.0.0";
        int PORT = 8000;

        // This parameters should be defined if running in AWS "java LoadBalancer 0.0.0.0 80"
        if(args.length == 2) {
            SERVER = args[0];
            PORT = Integer.parseInt(args[1]);
            Configs.loadBalancerInstanceId = Configs.initLbInstanceId();
        }

        try {
            System.out.println("Application starting...");
            AwsHandler.init();
            AutoScalerThread as = new AutoScalerThread();
            as.start();

            final HttpServer server = HttpServer.create(new InetSocketAddress(SERVER, PORT), 0);

            server.createContext("/scan", new LoadBalancerHandler());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println(server.getAddress().toString());
        } catch(Exception e) {
            System.out.println("Server error: " + e.getMessage());
            System.out.println(e.fillInStackTrace());
        }
    }
}
