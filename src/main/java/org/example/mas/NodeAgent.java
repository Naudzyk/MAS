package org.example.mas;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public abstract class NodeAgent extends Agent {
    protected static final Logger logger = LoggerFactory.getLogger(NodeAgent.class);

    protected String nodeName;      // Имя узла (для MasterAgent)
    protected String inventoryPath;
    protected String playbooksDir;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 3) {
            logger.error("NodeAgent requires: nodeName, inventoryPath, playbooksDir");
            doDelete();
            return;
        }

        this.nodeName = (String) args[0];
        this.inventoryPath = (String) args[1];
        this.playbooksDir = (String) args[2];

        logger.info("{} initialized for node/role: {}", getClass().getSimpleName(), nodeName);

        // Запуск циклического мониторинга
        addBehaviour(new CyclicBehaviour() {
            private long lastCheck = 0;
            private static final long CHECK_INTERVAL_MS = 10_000; // 10 сек

            @Override
            public void action() {
                long now = System.currentTimeMillis();
                if (now - lastCheck >= CHECK_INTERVAL_MS) {
                    checkServices(); // Абстрактный метод, реализуется в подклассах
                    lastCheck = now;
                }
                block(1000); // Не грузим CPU
            }
        });
    }

    // Абстрактный метод — реализуется в MasterAgent и WorkerAgent
    protected abstract void checkServices();

    // Универсальный метод отправки сообщения координатору
    protected void sendToCoordinator(String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("coordinator", AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
    }

    // Вспомогательный метод для выполнения kubectl и чтения вывода
    protected String executeKubectl(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder("kubectl");
            for (String arg : args) {
                pb.command().add(arg);
            }
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            logger.warn("Failed to execute kubectl: {}", String.join(" ", args), e);
            return null;
        }
    }
}