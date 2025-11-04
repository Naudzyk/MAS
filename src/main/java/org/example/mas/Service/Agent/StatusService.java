package org.example.mas.Service.Agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class StatusService {
    private final Map<String,Object> status = new ConcurrentHashMap<>();

    private static StatusService instance;

    public StatusService() {
        status.put("ansibleStage", "WAITING_FOR_DEPLOYMENT_START");
        status.put("clusterStatus", "NOT_DEPLOYED");
        status.put("alerts", new String[0]);
        status.put("diagnosticLogs", "");
        status.put("lastUpdate", System.currentTimeMillis());
        status.put("metric_", "ANALAZY");

        StatusService.instance = this;
    }
       public static StatusService getInstance() {
        return instance;
    }

    public Map<String, Object> getStatus() {
        return new HashMap<>(status);
    }

    public Object get(String key) {
        return status.get(key);
    }

    public void update(String key, String value) {
        status.put(key, value);
    }

    public void update(String key, Map<String, Object> metrics) {
        status.put(key, metrics);
    }

    public void update(String key,Object value) {
        status.put(key, value);
        status.put("lastUpdate", System.currentTimeMillis());
    }


}
