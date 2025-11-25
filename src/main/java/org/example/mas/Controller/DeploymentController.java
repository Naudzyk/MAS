package org.example.mas.Controller;

import lombok.RequiredArgsConstructor;
import org.example.mas.DTO.DeploymentRequest;
import org.example.mas.Service.DeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/deploy")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startDeployment(@RequestBody DeploymentRequest request) {
        try {
            String message = deploymentService.startDeployment(request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}

