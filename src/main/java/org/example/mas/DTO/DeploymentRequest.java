package org.example.mas.DTO;

import lombok.Data;

@Data
public class DeploymentRequest {
    private boolean skipBootstrap;
    private BootstrapRequest bootstrap;
}

