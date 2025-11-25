package org.example.mas.Service;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mas.Agent.BootstrapAgent;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BootstrapService {

    private final AgentContainer agentContainer;

    public void bootstrapNode(String ip, String username, String password, String publicKeyPath) {
        String resolvedKeyPath = resolvePath(publicKeyPath);
        try {
            AgentController controller = agentContainer.createNewAgent(
                "bootstrap-" + ip.replace(".", "-"),
                BootstrapAgent.class.getName(),
                new Object[]{ip, username, password, resolvedKeyPath}
            );
            controller.start();
            log.info("Started BootstrapAgent for {}", ip);
        } catch (Exception e) {
            log.error("Failed to start BootstrapAgent for {}: {}", ip, e.getMessage(), e);
        }
    }

    private String resolvePath(String path) {
        if (path == null || path.isBlank()) {
            return System.getProperty("user.home") + "/.ssh/id_ed25519.pub";
        }
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
