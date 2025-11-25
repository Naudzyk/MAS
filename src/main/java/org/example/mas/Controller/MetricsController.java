package org.example.mas.Controller;

import lombok.RequiredArgsConstructor;
import org.example.mas.Service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);
    private final StatusService statusService;

    @GetMapping("/{nodeName}")
    public ResponseEntity<Object> getMetrics(@PathVariable String nodeName) {
        logger.debug("Metrics for node {}", nodeName);

        String key = "metrics_" + nodeName;
        Object metric = statusService.get(key);
        if(metric != null) {
            logger.debug("Returning metrics for node {}: {}", nodeName, metric);
            return ResponseEntity.ok(metric);
        }else {
            logger.debug("Metrics not found for node: {}", nodeName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                     .body(Collections.singletonMap("error", "Metric not found"));
        }

    }
}
