package org.example.mas.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mas.Service.NodeService;
import org.example.mas.Service.TerraformService;
import org.example.mas.models.ClusterNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
@Slf4j
public class NodeController {

    private final NodeService nodeService;
    private final TerraformService terraformService;

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> saveNodes(@RequestBody List<ClusterNode> nodes) {
        nodeService.replaceNodes(nodes);
        Map<String, Object> response = new HashMap<>();
        response.put("saved", nodes.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<ClusterNode> listNodes() {
        return nodeService.getNodes();
    }

    @PostMapping("/generate-inventory")
    public ResponseEntity<Map<String, Object>> generateInventory() {
        try {
            log.info("Starting inventory generation");
            Path inventory = terraformService.generateInventory();
            Map<String, Object> response = new HashMap<>();
            response.put("inventoryPath", inventory.toString());
            response.put("success", true);
            log.info("Inventory generation successful: {}", inventory);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Inventory generation failed: {}", ex.getMessage(), ex);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", ex.getMessage());


            if (ex.getCause() != null) {
                errorResponse.put("details", ex.getCause().getMessage());
            }

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}

