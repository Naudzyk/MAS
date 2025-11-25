package org.example.mas.Controller;


import lombok.RequiredArgsConstructor;
import org.example.mas.DTO.StatusUpdateDTO;
import org.example.mas.Service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalAgentController {

    private static final Logger logger = LoggerFactory.getLogger(InternalAgentController.class);

    private final StatusService statusService;

    @PostMapping("/status")
    public ResponseEntity<Void> updateStatus(@RequestBody StatusUpdateDTO update) {
        logger.info("Received status update: {} = {}", update.getKey(), update.getValue());
        if (update.getKey() == null || update.getValue() == null) {
            return ResponseEntity.badRequest().build();
        }
        statusService.update(update.getKey(), update.getValue());
        return ResponseEntity.ok().build();
    }
}
