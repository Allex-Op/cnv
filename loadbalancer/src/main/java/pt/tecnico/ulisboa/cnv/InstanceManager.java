package pt.tecnico.ulisboa.cnv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstanceManager {
    public static List<EC2Instance> instances = Collections.synchronizedList(new ArrayList<>());


    /**
     * It checks if all the instances registered
     * are still alive. In case they are down the
     * specified object should be deleted.
     */
    public static void checkInstancesHealthStatus() {
        for (EC2Instance instance : instances) {
            String url = Configs.urlBuild(instance.getInstanceIp());
            boolean alive = sendHttpRequest(url + "health");

            if(!alive) {
                instance.incrementFailedHealthChecks();
                if(instance.getFailedHealthChecks() > Configs.MAX_FAILED_HEALTH_CHECKS) {
                    System.out.println("[Auto Scaler] Instance removed for failing the maximum health checks.");
                    instances.remove(instance);
                }
            } else {
                instance.setFailedHealthChecks(0);
            }
        }
    }

    private static boolean sendHttpRequest(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return response.contains("alive");
        } catch (Exception e) {
            System.out.println("[Auto Scaler] Failed sending HTTP request (healthcheck). Instance unavailable.");
            return false;
        }
    }
}
