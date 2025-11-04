package org.example.mas.Service.Agent;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.CurrencyNameProvider;

@Service
public class StatusService {
    Map<String, Object> metrics = new HashMap<>();
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

    public void update(String key, String value) {
        status.put(key, value);
    }

    public void update(String key, Map<String, Object> metrics) {
        status.put(key, metrics );
    }


}
