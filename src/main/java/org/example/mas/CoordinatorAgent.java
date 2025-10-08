package org.example.mas;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import org.example.mas.Agent.MasterAgent;
import org.example.mas.Agent.WorkerAgent;
import org.example.mas.utils.AnsibleRunner;
import org.example.mas.utils.InventoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CoordinatorAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);
    private String inventory;
    private String playbooksDir;
    private final Map<String, AID> nodeAgents = new HashMap<>();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 2) {
            logger.error("CoordinatorAgent requires: inventoryPath, playbooksDir");
            doDelete();
            return;
        }

        this.inventory = (String) args[0];
        this.playbooksDir = (String) args[1];
        logger.info("CoordinatorAgent initialized with inventory: {}, playbooksDir: {}", inventory, playbooksDir);

        // === ФАЗА 1: Инициализация кластера (однократно) ===
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                logger.info("Starting initial cluster deployment...");

                // Запускаем Ansible-плейбуки для полной настройки
                String[] playbooks = {
                        "01_system_preparation.yml",
                        "02_containerd.yml",
                        "03_kubernetes_install.yml",
                        "04_kubernetes_init.yml",
                        "05_calico_cni.yml",
                        "06_worker_preparation.yml",
                        "07_worker_join.yml",
                        "08_htcondor.yml"
                };

                for (String playbook : playbooks) {
                    logger.info("Running playbook: {}", playbook);
                    if (!AnsibleRunner.run(playbook, inventory, playbooksDir, 15)) {
                        logger.error("Deployment failed at playbook: {}", playbook);
                        DashboardServer.updateStatus("alerts", new String[]{
                                "htcondor-execute-rh3q: CrashLoopBackOff",
                                "Проверьте логи пода"
                        });
                        return;
                    }
                }

                logger.info("Initial cluster deployment completed. Creating node agents...");
                DashboardServer.updateStatus("ansibleStage", "kubernetes_init ✅ | htcondor ⏳");
                createNodeAgents(); // ← создаём агентов для каждого узла
            }
        });

        // Этап 2: слушать сообщения от узлов
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    handleNodeMessage(msg);
                } else block();
            }
        });
    }

    private void createNodeAgents() {
        try {
            // Парсим inventory, используя this.inventory
            InventoryParser.Inventory inv = InventoryParser.parse(this.inventory);

            // Создаём агента для master-узлов
            for (InventoryParser.Host master : inv.getGroup("master")) {
                createNodeAgent(master.name, true);
            }

            // Создаём агентов для worker-узлов
            for (InventoryParser.Host worker : inv.getGroup("workers")) {
                createNodeAgent(worker.name, false);
            }

        } catch (Exception e) {
            logger.error("Failed to create node agents from inventory", e);
        }
    }

    private void createNodeAgent(String nodeName, boolean isMaster) {
        try {
            String agentType = isMaster ? MasterAgent.class.getName() : WorkerAgent.class.getName();

            // Передаём аргументы агенту
            Object[] agentArgs = new Object[]{
                    nodeName,           // имя узла
                    this.inventory,     // путь к inventory
                    this.playbooksDir   // директория с плейбуками
            };

            AgentController ac = getContainerController().createNewAgent(
                    nodeName,
                    agentType,
                    agentArgs
            );
            ac.start();

            // Сохраняем AID для отправки сообщений
            AID agentAID = new AID(nodeName, AID.ISLOCALNAME);
            nodeAgents.put(nodeName, agentAID);

            logger.info("Created {} agent: {}", isMaster ? "master" : "worker", nodeName);
        } catch (Exception e) {
            logger.error("Failed to create agent for node: " + nodeName, e);
        }
    }

    private void handleNodeMessage(ACLMessage msg) {
        String sender = msg.getSender().getLocalName();
        String content = msg.getContent();

        if (content.startsWith("ALERT:")) {
            System.out.println("Coordinator: Received alert from " + sender + ": " + content);
            // Реакция на сбой: перезапуск, уведомление и т.д.
        } else if (content.equals("READY")) {
            System.out.println("Coordinator: Node " + sender + " is ready");
        }
    }
}
