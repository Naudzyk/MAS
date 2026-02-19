package org.example.mas.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatusService {
    private static final Logger logger = LoggerFactory.getLogger(StatusService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, Object> status = new ConcurrentHashMap<>();

    public StatusService() {
        status.put("ansibleStage", "WAITING_FOR_DEPLOYMENT_START");
        status.put("clusterStatus", "NOT_DEPLOYED");
        status.put("alerts", new String[0]);
        status.put("diagnosticLogs", "");
        status.put("lastUpdate", System.currentTimeMillis());
        status.put("activeNodes", new ArrayList<String>());
        status.put("bootstrapStatus", "PENDING");
    }

    public Map<String, Object> getStatus() {
        return new HashMap<>(status);
    }

    public Object get(String key) {
        return status.get(key);
    }

    public void update(String key, String jsonString) {
        logger.info("### StatusService updated: {} = {}", key, jsonString);
        try {
            if (jsonString != null && jsonString.trim().startsWith("{") && jsonString.endsWith("}")) {
                Map<String, Object> obj = OBJECT_MAPPER.readValue(jsonString, Map.class);
                status.put(key, obj);
            } else {
                status.put(key, jsonString);
            }
        } catch (Exception e) {
            status.put(key, jsonString);
        }
        status.put("lastUpdate", System.currentTimeMillis());
    }



}
