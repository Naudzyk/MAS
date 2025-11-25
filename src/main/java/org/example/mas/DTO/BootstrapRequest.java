package org.example.mas.DTO;

import org.example.mas.models.BootstrapNode;

import java.util.List;

public class BootstrapRequest {
    private List<BootstrapNode> nodes;
    private String publicKeyPath = "~/.ssh/id_ed25519.pub";
}
