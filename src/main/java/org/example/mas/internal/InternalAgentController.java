package org.example.mas.internal;

import lombok.RequiredArgsConstructor;
import org.example.mas.DTO.StatusUpdateDTO;
import org.example.mas.Service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InternalAgentController {

    private static final Logger logger = LoggerFactory.getLogger(InternalAgentController.class);

    private final StatusService statusService;

    /** Для CI/CD: опрос статуса кластера (clusterStatus = "DEPLOY CLUSTER" означает READY). */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(statusService.getStatus());
    }

    @PostMapping("/internal/status")
    public ResponseEntity<Void> updateStatus(@RequestBody StatusUpdateDTO update) {
        // POST /api/internal/status — приём обновлений от агентов
        logger.info("Received status update: {} = {}", update.getKey(), update.getValue());
        if (update.getKey() == null || update.getValue() == null) {
            return ResponseEntity.badRequest().build();
        }
        statusService.update(update.getKey(), update.getValue());
        return ResponseEntity.ok().build();
    }
}
