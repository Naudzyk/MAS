package org.example.mas.Controller;


import lombok.RequiredArgsConstructor;
import org.example.mas.DTO.StatusUpdateDTO;
import org.example.mas.Service.Agent.StatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalAgentController {

    private final StatusService statusService;

    @PostMapping("/status")
    public ResponseEntity<Void> updateStatus(@RequestBody StatusUpdateDTO update) {
        if (update.getKey() == null || update.getValue() == null) {
            return ResponseEntity.badRequest().build();
        }
        statusService.update(update.getKey(), update.getValue());
        return ResponseEntity.ok().build();
    }
}
