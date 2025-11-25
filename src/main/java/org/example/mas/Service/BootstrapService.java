package org.example.mas.Service;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;

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

    // Получаем контейнер из глобального JADE runtime
    private AgentContainer getMainContainer() {
        try {
            Runtime runtime = Runtime.instance();
            Profile profile = new ProfileImpl();
            // Настройки профиля (если нужны)
            return runtime.createMainContainer(profile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get JADE main container", e);
        }
    }

    public void bootstrapNode(String ip, String username, String password,
                            String publicKeyPath, String coordinatorName) {
        if (publicKeyPath.startsWith("~")) {
            publicKeyPath = System.getProperty("user.home") + publicKeyPath.substring(1);
        }

        try {
            AgentContainer container = getMainContainer();

            AgentController ac = container.createNewAgent(
                "bootstrap-" + ip.replace(".", "-"),
                BootstrapAgent.class.getName(),
                new Object[]{ip, username, password, publicKeyPath, coordinatorName}
            );
            ac.start();
            log.info("Started BootstrapAgent for {}", ip);

        } catch (Exception e) {
            log.error("Failed to start BootstrapAgent for {}: {}", ip, e.getMessage(), e);
        }
    }
}
