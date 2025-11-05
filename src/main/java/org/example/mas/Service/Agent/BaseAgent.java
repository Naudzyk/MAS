package org.example.mas.Service.Agent;

import jade.core.Agent;
import org.example.mas.DTO.StatusUpdateDTO;
import org.example.mas.SpringContextHelper;
import org.springframework.web.reactive.function.client.WebClient;

public abstract class BaseAgent extends Agent {

    private WebClient getWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
    }

    protected void sendStatusUpdate(String key, String value) {
        try {
            getWebClient()
                .post()
                .uri("/api/internal/status")
                .bodyValue(new StatusUpdateDTO(key, value))
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            System.err.println("Failed to send status update: " + key + "=" + value);
            e.printStackTrace();
        }
    }
}
