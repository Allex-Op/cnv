import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public class LoadBalancerHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange t) throws IOException {
        final String query = t.getRequestURI().getQuery();
        System.out.println("> Query:\t" + query);

        RequestArguments args = new RequestArguments(query);
        Job job = new Job(args);

        LbStrategy.distributeRequest(job);

        String response = "I am alive?";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
