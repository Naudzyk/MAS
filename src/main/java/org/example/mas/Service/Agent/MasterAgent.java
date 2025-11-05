package org.example.mas.Service.Agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import lombok.RequiredArgsConstructor;
import org.example.mas.utils.InventoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


public class MasterAgent extends BaseAgent {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Logger logger = LoggerFactory.getLogger(MasterAgent.class);
    private String nodeName;
    private String inventoryPath;



    @Override
    protected void setup() {
        Object[] args = getArguments();
        this.nodeName = (String) args[0];
        this.inventoryPath = (String) args[1];


        logger.info("MasterAgent {} initialized for node: {}", getLocalName(), nodeName);

        addBehaviour(new TickerBehaviour(this, 10_000) { // Каждые 10 секунд
            @Override
            protected void onTick() {
                collectMetrics();
            }
        });
    }
    private void collectMetrics() {
    try {
        String nodeIp = getNodeIpFromInventory(nodeName);
        if (nodeIp == null) {
            logger.warn("IP for node {} not found", nodeName);
            return;
        }

        String prometheusUrl = "http://localhost:9090";

        String cpuQuery = "100 - (avg by (instance) (rate(node_cpu_seconds_total{instance=\"" + nodeIp + ":9100\",mode=\"idle\"}[1m])) * 100)";
        JsonObject cpuResponse = queryPrometheus(prometheusUrl, cpuQuery);
        double cpuLoad = parsePrometheusValue(cpuResponse);

        String memQuery = "(node_memory_MemTotal_bytes{instance=\"" + nodeIp + ":9100\"} - node_memory_MemAvailable_bytes{instance=\"" + nodeIp + ":9100\"}) / node_memory_MemTotal_bytes{instance=\"" + nodeIp + ":9100\"} * 100";
        JsonObject memResponse = queryPrometheus(prometheusUrl, memQuery);
        double memUsage = parsePrometheusValue(memResponse);

        String diskQuery = "(node_filesystem_size_bytes{instance=\"" + nodeIp + ":9100\",fstype!=\"tmpfs\",mountpoint=\"/\"} - node_filesystem_free_bytes{instance=\"" + nodeIp + ":9100\",fstype!=\"tmpfs\",mountpoint=\"/\"}) / node_filesystem_size_bytes{instance=\"" + nodeIp + ":9100\",fstype!=\"tmpfs\",mountpoint=\"/\"} * 100";
        JsonObject diskResponse = queryPrometheus(prometheusUrl, diskQuery);
        double diskUsage = parsePrometheusValue(diskResponse);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cpuLoad", String.format("%.2f", cpuLoad));
        metrics.put("memoryUsage", String.format("%.2f", memUsage));
        metrics.put("diskUsage", String.format("%.2f", diskUsage));
        metrics.put("timestamp", System.currentTimeMillis());

        ObjectMapper objectMapper = new ObjectMapper();
        String metricsJson = objectMapper.writeValueAsString(metrics);

        sendStatusUpdate("metrics_" + nodeName, metricsJson);
        logger.info("Collected metrics for {}: CPU: {}%, MEM: {}%, DISK: {}%", nodeName, cpuLoad, memUsage, diskUsage);

    } catch (Exception e) {
        logger.error("Failed to collect metrics for node: " + nodeName, e);
    }
    }
    private JsonObject queryPrometheus(String baseUrl, String query) throws Exception {
    String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
    String url = baseUrl + "/api/v1/query?query=" + encodedQuery;

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
        throw new RuntimeException("Prometheus returned HTTP " + response.statusCode());
    }

    return JsonParser.parseString(response.body()).getAsJsonObject();
}

private double parsePrometheusValue(JsonObject response) {
    try {
        if ("success".equals(response.get("status").getAsString())) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("result") && data.getAsJsonArray("result").size() > 0) {
                JsonObject result = data.getAsJsonArray("result").get(0).getAsJsonObject();
                if (result.has("value") && result.getAsJsonArray("value").size() > 1) {
                    return result.getAsJsonArray("value").get(1).getAsDouble();
                }
            }
        }
    } catch (Exception e) {
        logger.warn("Failed to parse Prometheus value", e);
    }
    return 0.0;
}


    private String getNodeIpFromInventory(String nodeName) {
        try {
            InventoryParser.Inventory inv = InventoryParser.parse(inventoryPath);
            for (InventoryParser.Host host : inv.getAllHosts()) {
                if (host.name.equals(nodeName)) {
                    return host.getHost();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse inventory", e);
        }
        return null;
    }


}