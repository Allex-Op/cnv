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
        System.out.println("> Query:\t" + query);

        RequestArguments args = new RequestArguments(query);
        Job job = new Job(args);

        // Distribute request & wait for the answer
        byte[] response = LbStrategy.distributeRequest(job, query);

        t.sendResponseHeaders(200, response.length);
        OutputStream os = t.getResponseBody();
        os.write(response);
        os.close();
    }
}
