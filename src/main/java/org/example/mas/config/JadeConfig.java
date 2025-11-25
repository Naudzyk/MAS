package org.example.mas.config;

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
        AgentContainer container = runtime.createMainContainer(profile);
        logger.info("JADE AgentContainer initialized");
        return container;
    }
}

