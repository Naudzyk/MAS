package org.example.mas.DTO;

import lombok.Data;
import org.example.mas.models.BootstrapNode;

import java.util.ArrayList;
import java.util.List;

@Data
public class BootstrapRequest {
    private List<BootstrapNode> nodes = new ArrayList<>();
    private String publicKeyPath = "~/.ssh/id_ed25519.pub";
}
