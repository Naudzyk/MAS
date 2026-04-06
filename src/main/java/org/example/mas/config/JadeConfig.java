package org.example.mas.config;

import java.io.IOException;
import java.net.ServerSocket;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JadeConfig {

    private static final Logger logger = LoggerFactory.getLogger(JadeConfig.class);

    @Bean(destroyMethod = "kill")
    public AgentContainer agentContainer() {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");


        int freePort = 0;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        } catch (IOException e) {
            logger.warn("Could not allocate free TCP port for JADE, falling back to default port 1099", e);
            freePort = 1099;
        }
        profile.setParameter(Profile.MAIN_PORT, String.valueOf(freePort));

        AgentContainer container = runtime.createMainContainer(profile);
        logger.info("JADE AgentContainer initialized");
        return container;
    }
}

