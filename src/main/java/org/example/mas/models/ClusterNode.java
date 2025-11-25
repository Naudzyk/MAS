package org.example.mas.models;

import lombok.Data;

@Data
public class ClusterNode {
    private String inventoryName;
    private String ipAddress;
    private String role;
    private String Password;
}
