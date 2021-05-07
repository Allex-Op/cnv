import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LoadBalancerApplication {
    public static void main(String[] args) {
        String SERVER = "0.0.0.0";
        int PORT = 80;

        // In case there are other non default parameters
        if(args.length == 2) {
            SERVER = args[0];
            PORT = Integer.parseInt(args[1]);
        }

        try {
            AwsHandler.init();

            final HttpServer server = HttpServer.create(new InetSocketAddress(SERVER, PORT), 0);

            server.createContext("/scan", new LoadBalancerHandler());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println(server.getAddress().toString());
        } catch(Exception e) {
            System.out.println("Server error");
        }
    }
}
