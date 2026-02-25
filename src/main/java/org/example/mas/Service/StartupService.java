package org.example.mas.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupService implements ApplicationListener<ApplicationReadyEvent> {

    private final DeploymentService deploymentService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            String result = deploymentService.startDeploymentFromInventory();
            log.info("Авто-старт: {}", result);
        } catch (Exception e) {
            log.error("Не удалось выполнить авто-запуск из inventory.ini: {}", e.getMessage(), e);
        }
    }
}
