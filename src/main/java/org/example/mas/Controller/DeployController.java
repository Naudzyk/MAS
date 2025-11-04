package org.example.mas.Controller;

import lombok.RequiredArgsConstructor;
import org.example.mas.Service.Agent.StatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeployController {

    private final StatusService statusService;

    @GetMapping("/status")
    public ResponseEntity<Map<String,Object>> getStatus() {
        Map<String,Object> currentState = statusService.getStatus();

        return ResponseEntity.ok().body(currentState);

    }


}
