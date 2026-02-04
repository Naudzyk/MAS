package org.example.mas.Service;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mas.Agent.CoordinatorAgent;
import org.example.mas.DTO.BootstrapRequest;
import org.example.mas.DTO.DeploymentRequest;
import org.example.mas.models.BootstrapNode;
import org.example.mas.utils.InventoryParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

    private final AgentContainer agentContainer;
    private final BootstrapService bootstrapService;
    private final StatusService statusService;

    @Value("${mas.paths.inventory:inventory.ini}")
    private String inventoryPath;

    @Value("${mas.paths.playbooks:scripts}")
    private String playbooksDir;

    @Value("${mas.bootstrap.group:bootstrap}")
    private String bootstrapGroup;

    @Value("${mas.bootstrap.public-key:~/.ssh/id_ed25519.pub}")
    private String defaultPublicKeyPath;

    private final Set<String> pendingBootstrap = ConcurrentHashMap.newKeySet();
    private volatile boolean coordinatorStarted = false;
    private volatile boolean autoStartAfterBootstrap = false;

    public synchronized String startDeployment(DeploymentRequest request) {
        if (coordinatorStarted) {
            return "Coordinator уже запущен.";
        }

        if (!request.isSkipBootstrap()) {
            BootstrapRequest bootstrap = request.getBootstrap();
            if (bootstrap == null || CollectionUtils.isEmpty(bootstrap.getNodes())) {
                throw new IllegalArgumentException("Для первичной настройки нужны узлы с логином/паролем.");
            }
            startBootstrapPhase(bootstrap, true);
            return "Bootstrap-agent запущен. Координатор стартует автоматически после завершения.";
        }

        statusService.update("bootstrapStatus", "SKIPPED");
        startCoordinatorInternal();
        return "Координатор запущен без Bootstrap.";
    }

    public synchronized String startDeploymentFromInventory() {
        if (coordinatorStarted) {
            return "Coordinator уже запущен.";
        }

        BootstrapRequest bootstrapRequest = buildBootstrapRequestFromInventory();
        if (CollectionUtils.isEmpty(bootstrapRequest.getNodes())) {
            statusService.update("bootstrapStatus", "SKIPPED");
            startCoordinatorInternal();
            return "Координатор запущен (Bootstrap пропущен).";
        }

        startBootstrapPhase(bootstrapRequest, true);
        return "Bootstrap-agent запущен из inventory. Координатор стартует автоматически после завершения.";
    }

    public synchronized String startBootstrapOnly(BootstrapRequest request) {
        if (CollectionUtils.isEmpty(request.getNodes())) {
            throw new IllegalArgumentException("Добавьте хотя бы один узел для Bootstrap.");
        }
        startBootstrapPhase(request, false);
        return "Bootstrap-agent запущен.";
    }

    private void startBootstrapPhase(BootstrapRequest request, boolean autoStartCoordinator) {
        pendingBootstrap.clear();
        request.getNodes().stream()
            .filter(this::isNodeValid)
            .forEach(node -> pendingBootstrap.add(node.getIp()));
        if (pendingBootstrap.isEmpty()) {
            throw new IllegalArgumentException("Некорректные параметры узлов для Bootstrap.");
        }
        autoStartAfterBootstrap = autoStartCoordinator;
        statusService.update("bootstrapStatus", "IN_PROGRESS");
        for (BootstrapNode node : request.getNodes()) {
            if (isNodeValid(node)) {
                bootstrapService.bootstrapNode(node.getIp(), node.getUsername(), node.getPassword(), request.getPublicKeyPath());
            }
        }
    }

    private BootstrapRequest buildBootstrapRequestFromInventory() {
        Path inventory = Paths.get(inventoryPath).toAbsolutePath();
        if (!Files.exists(inventory)) {
            throw new IllegalStateException("Inventory не найден: " + inventory);
        }

        InventoryParser.Inventory parsedInventory;
        try {
            parsedInventory = InventoryParser.parse(inventory.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать inventory: " + inventory, e);
        }

        List<InventoryParser.Host> hosts = parsedInventory.getGroup(bootstrapGroup);
        BootstrapRequest request = new BootstrapRequest();
        request.setPublicKeyPath(resolveBootstrapPublicKey(hosts));

        List<BootstrapNode> nodes = new ArrayList<>();
        for (InventoryParser.Host host : hosts) {
            String ip = host.getHost();
            String username = firstVar(host, "bootstrap_user", "ansible_user", "user");
            String password = firstVar(host, "bootstrap_password", "bootstrap_pass", "password");

            if (ip == null || username == null || password == null) {
                log.warn("Пропущен узел bootstrap (нет ip/user/password): {}", host.name);
                continue;
            }

            BootstrapNode node = new BootstrapNode();
            node.setIp(ip);
            node.setUsername(username);
            node.setPassword(password);
            nodes.add(node);
        }

        request.setNodes(nodes);
        if (!hosts.isEmpty() && nodes.isEmpty()) {
            throw new IllegalArgumentException("В группе '" + bootstrapGroup + "' нет узлов с валидными учетными данными.");
        }

        return request;
    }

    private String resolveBootstrapPublicKey(List<InventoryParser.Host> hosts) {
        for (InventoryParser.Host host : hosts) {
            String value = firstVar(host,
                "bootstrap_public_key",
                "bootstrap_public_key_path",
                "bootstrap_pubkey",
                "bootstrap_key_path"
            );
            if (value != null) {
                return value;
            }
        }
        return defaultPublicKeyPath;
    }

    private String firstVar(InventoryParser.Host host, String... keys) {
        for (String key : keys) {
            String value = host.vars.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public void notifyBootstrapResult(String ip, boolean success) {
        if (success) {
            pendingBootstrap.remove(ip);
            statusService.update("bootstrapStatus", pendingBootstrap.isEmpty() ? "COMPLETED" : "IN_PROGRESS");
            if (autoStartAfterBootstrap && pendingBootstrap.isEmpty()) {
                startCoordinatorInternal();
            }
        } else {
            pendingBootstrap.clear();
            statusService.update("bootstrapStatus", "FAILED:" + ip);
        }
    }

    private synchronized void startCoordinatorInternal() {
        if (coordinatorStarted) {
            log.info("Coordinator уже активен.");
            return;
        }
        Path inventory = Paths.get(inventoryPath).toAbsolutePath();
        Path scripts = Paths.get(playbooksDir).toAbsolutePath();
        if (!Files.exists(inventory)) {
            throw new IllegalStateException("Inventory не найден: " + inventory);
        }
        if (!Files.exists(scripts)) {
            throw new IllegalStateException("Каталог playbooks не найден: " + scripts);
        }

        try {
            String agentName = "coordinator-" + System.currentTimeMillis();
            AgentController controller = agentContainer.createNewAgent(
                agentName,
                CoordinatorAgent.class.getName(),
                new Object[]{inventory.toString(), scripts.toString()}
            );
            controller.start();
            coordinatorStarted = true;
            statusService.update("clusterStatus", "DEPLOYING");
            log.info("CoordinatorAgent {} запущен.", agentName);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось запустить CoordinatorAgent", e);
        }
    }

    private boolean isNodeValid(BootstrapNode node) {
        return node != null
            && node.getIp() != null
            && node.getUsername() != null
            && node.getPassword() != null;
    }
}

