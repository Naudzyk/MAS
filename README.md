### HTCondor Multi‑Agent Deployment System (JADE + Ansible)

Система оркестрации, которая поэтапно разворачивает кластер Kubernetes и HTCondor с помощью агентов JADE, запускающих Ansible‑плейбуки. Порядок этапов строго последовательный и контролируется координатором.

### Что делает
- Этапы: системная подготовка → containerd → установка Kubernetes → инициализация Kubernetes → Calico CNI → подготовка воркеров → присоединение воркеров → HTCondor.
- Каждый этап исполняет отдельный агент. `CoordinatorAgent` шлёт `START_*`, агент запускает один плейбук и отвечает `COMPLETE`/`FAILED`.

### Требования
- Машина управления (Linux):
  - Java 17+ (OpenJDK)
  - Ansible 2.13+ (Python 3)
  - SSH‑доступ к целевым хостам
- Целевые узлы (manager и worker): Debian/Ubuntu с systemd.

### Структура
- `MAS/target/MAS-1.0-SNAPSHOT.jar` — собранное приложение (имя может отличаться)
- `inventory.ini` — Ansible‑инвентарь
- `scripts/` — каталог с плейбуками:
  - `01_system_preparation.yml`
  - `02_containerd.yml`
  - `03_kubernetes_install.yml`
  - `04_kubernetes_init.yml`
  - `05_calico_cni.yml`
  - `06_worker_preparation.yml`
  - `07_worker_join.yml`
  - `08_htcondor.yml`

### Пример inventory.ini
```ini
[central_manager]
mas ansible_host=192.168.56.107 ansible_user=vboxuser ansible_become=true

[worker]
execute ansible_host=192.168.56.105 ansible_user=vboxuser ansible_become=true
```
### Папка со scripts 
 Скрипты можно взять тут https://github.com/Naudzyk/Automation-DevOps-Kubernetes-Containerd/tree/main/scripts 
 

### Запуск приложения

1. Соберите фронтенд и бекэнд (`mvn spring-boot:run` или запуск jar).
2. Перейдите в веб-панель `http://localhost:8080`.
3. Добавьте узлы и сохраните их — сервис сохранит данные и передаст их в Terraform.
4. Нажмите «Сгенерировать inventory» (кнопка «Развернуть кластер» сама вызывает `/api/nodes/generate-inventory`). Terraform создаст `inventory.ini` из данных веб-панели.
5. При первом запуске оставьте чекбокс «Пропустить Bootstrap» выключенным, заполните форму первоначальной настройки и нажмите «Развернуть кластер». Будут созданы `BootstrapAgent` для каждого хоста, настроены SSH-ключи и sudo, затем автоматически стартует `CoordinatorAgent`.
6. Если хосты уже подготовлены, отметьте чекбокс и агент-координатор стартует сразу, минуя Bootstrap.