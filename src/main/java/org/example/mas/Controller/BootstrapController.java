package org.example.mas.Controller;

import lombok.RequiredArgsConstructor;
import org.example.mas.DTO.BootstrapRequest;
import org.example.mas.Service.DeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final DeploymentService deploymentService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startBootstrap(@RequestBody BootstrapRequest request) {
        try {
            String message = deploymentService.startBootstrapOnly(request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}

