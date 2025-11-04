package org.example.mas.Controller;

import jade.content.abs.AbsAgentAction;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import lombok.RequiredArgsConstructor;
import org.example.mas.Service.Agent.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LoggerController {

    private static final Logger logger = LoggerFactory.getLogger(LoggerController.class);

    private final StatusService statusService;
    private final AgentContainer agentContainer;




    @GetMapping()
    public ResponseEntity<String> getLogs() {
        String logsFilePath = (String) statusService.get("diagnosticLogs");

        if (logsFilePath != null && !logsFilePath.isEmpty()) {
            Path logFile = Paths.get(logsFilePath);
            if (logFile != null && Files.exists(logFile)) {
                try {
                    String content = Files.readString(logFile, StandardCharsets.UTF_8);
                    return ResponseEntity.ok().header("Content-Type", "text/plain; charset=utf-8").body(content);
                }catch (Exception e) {
                    logger.error("Failed to read logFile " + e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("Content-Type", "text/plain").body("Failed to read logs" + e.getMessage());
                }
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).header("Content-Type", "text/plain").body("No logs found");
    }

    @PostMapping("/collect-logs")
    public ResponseEntity<Map<String,Object>> collectLogs() {
        try {
            AgentController ac = agentContainer.getAgent("coordinator");
            if (ac == null) {
                logger.error("No coordinator found");
                return ResponseEntity.status(500).header("Content-Type", "text/plain").body(Collections.singletonMap("message", "No coordinator agent not found"));
            }
            logger.info("Sending 'COLLECT_DIAGNOSTIC_LOGS' command to coordinator agent");
            ac.putO2AObject("COMMAND: COLLECT_DIAGNOSTIC_LOGS", false);
            logger.info("Sending 'COLLECT_DIAGNOSTIC_LOGS' command");
            return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Diagnostic logs collection initiated"
            ));
        }catch (Exception e) {
            logger.error("Failed to collect logs");
        }
        return ResponseEntity.status(500).header("Content-Type", "text/plain").body(Collections.singletonMap("message", "Failed to collect logs"));

    }






}
