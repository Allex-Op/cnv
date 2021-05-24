package pt.tecnico.ulisboa.cnv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import pt.tecnico.ulisboa.cnv.model.RequestArguments;

import java.io.IOException;
import java.io.OutputStream;

public class LoadBalancerHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange t) throws IOException {
        final String query = t.getRequestURI().getQuery();

        System.out.println();
        RequestArguments args = new RequestArguments(query);
        Job job = new Job(args);
        System.out.println("[LB Handler] New job with id: " + job.id + ", it's expected cost is: " + job.expectedCost);

        // Distribute request & wait for the answer
        byte[] response = LbStrategy.distributeRequest(job, query);
        System.out.println("[LB Handler] Request with id: " + job.id + " just finished. Sending response to client.");

        t.sendResponseHeaders(200, response.length);
        OutputStream os = t.getResponseBody();
        os.write(response);
        os.close();
    }
}
