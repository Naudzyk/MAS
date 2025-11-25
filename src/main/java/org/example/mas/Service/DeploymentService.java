package org.example.mas.Service;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mas.Agent.CoordinatorAgent;
import org.example.mas.DTO.BootstrapRequest;
import org.example.mas.DTO.DeploymentRequest;
import org.example.mas.models.BootstrapNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

