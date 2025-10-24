package org.example.mas;


import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// Пока не используется
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




}