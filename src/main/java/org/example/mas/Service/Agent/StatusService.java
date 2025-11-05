package org.example.mas.Service.Agent;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class StatusService {
    private final Map<String,Object> status = new ConcurrentHashMap<>();

    public StatusService() {
        status.put("ansibleStage", "WAITING_FOR_DEPLOYMENT_START");
        status.put("clusterStatus", "NOT_DEPLOYED");
        status.put("alerts", new String[0]);
        status.put("diagnosticLogs", "");
        status.put("lastUpdate", System.currentTimeMillis());
        status.put("metric_", "ANALAZY");

    }

    public Map<String, Object> getStatus() {
        return new HashMap<>(status);
    }

    public Object get(String key) {
        return status.get(key);
    }

    public void update(String key, String jsonString) {
        try {
            if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
                Map<String, Object> obj = new ObjectMapper().readValue(jsonString, Map.class);
                status.put(key, obj);
            } else {
                status.put(key, jsonString);
            }
        } catch (Exception e) {
            // Если не JSON — сохраняем как строку
            status.put(key, jsonString);
        }
        status.put("lastUpdate", System.currentTimeMillis());
    }



}
