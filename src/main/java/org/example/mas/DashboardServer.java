package org.example.mas;

import com.google.gson.Gson;
import spark.Spark;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DashboardServer {

    // Общее состояние, обновляемое агентами
    public static final Map<String, Object> STATUS = new ConcurrentHashMap<>();

    static {
        // Инициализация по умолчанию
        STATUS.put("ansibleStage", "not_started");
        STATUS.put("htcondorStatus", "unknown");
        STATUS.put("alerts", new String[0]);
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }

    public static void start(int port) {
        Spark.port(port);

        // API: получение текущего состояния
        Spark.get("/api/status", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(STATUS);
        });

        // Статические файлы (HTML, CSS, JS)
        Spark.staticFileLocation("/public");

        System.out.println("Dashboard available at http://localhost:" + port);
    }

    // Метод для обновления статуса из агентов
    public static void updateStatus(String key, Object value) {
        STATUS.put(key, value);
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }
}
