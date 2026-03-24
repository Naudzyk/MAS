### HTCondor Multi‑Agent Deployment System (JADE + Ansible)

Система оркестрации, которая поэтапно разворачивает кластер Kubernetes и HTCondor с помощью агентов JADE, запускающих Ansible‑плейбуки.

### Что делает
- Этапы: системная подготовка → containerd → установка Kubernetes → инициализация Kubernetes → Calico CNI → подготовка воркеров → присоединение воркеров → HTCondor.
- Каждый этап исполняет отдельный агент. 

### Требования:
  - Java 17+ (OpenJDK)
  - Ansible 2.13+ (Python 3)
  - SSH‑доступ к целевым хостам
  -  Целевые узлы (manager и worker): **Debian**, **Ubuntu**, **AstraLinux**, **RHEL**, **Rocky**, **AlmaLinux**, **Red OS (Рэд ОС)** — с systemd.

### Структура
- `target/MAS-1.0-SNAPSHOT.jar` — собранное приложение (имя может отличаться)
- `scripts/inventory.ini` — Ansible‑инвентарь
- `scripts/` — каталог с плейбуками

### Минимальное развёртывание на узле (только JAR + scripts)

Чтобы не копировать весь проект (исходники, pom.xml и т.д.), соберите дистрибутив и перенесите на узел только его:

1. **Сборка дистрибутива** (на машине с Maven):
   ```bash
   mvn package -Pdist
   ```
   В каталоге `target/mas-deploy/` появятся:
   - `MAS-1.0-SNAPSHOT.jar`
   - `scripts/` — плейбуки, inventory, vars, group_vars, os, шаблоны

2. **Копирование на узел** (одной папкой):
   ```bash
   scp -r target/mas-deploy user@node:/opt/mas
   # или: rsync -av target/mas-deploy/ user@node:/opt/mas/
   ```

3. **Запуск на узле** из каталога дистрибутива:
   ```bash
   cd /opt/mas
   export CONDOR_CLUSTER_PASSWORD="ваш_пароль"
   # при необходимости отредактируйте scripts/inventory.ini
   java -jar MAS-1.0-SNAPSHOT.jar
   ```

Пути в приложении заданы относительно рабочего каталога: `scripts/inventory.ini` и `scripts/` — поэтому запускать нужно из папки, где лежат `jar` и каталог `scripts`.


### Конфигурация (application.yml)
Минимальные настройки:
```yaml
mas:
  mode: default            # default | existing-cluster
  paths:
    inventory: scripts/inventory.ini
    playbooks: scripts
  bootstrap:
    group: bootstrap
    public-key: ~/.ssh/id_ed25519.pub
```

Дополнительно можно управлять последовательностью плейбуков:
```yaml
mas:
  playbooks:
    skip: 02_containerd.yml,03_kubernetes_install.yml
    # sequence: 08_htcondor.yml
```

### Роли групп inventory
- `[bootstrap]` — узлы, куда нужно зайти по паролю и настроить SSH‑ключ + sudo.
- `[central_manager]` — управляющий узел кластера (master/manager).
- `[execute_nodes]` — рабочие узлы (workers).

### Настройка inventory.ini
Bootstrap‑учётные данные читаются из группы `[bootstrap]`. Для каждого узла нужны:
- `ansible_host` — IP хоста
- `ansible_user` — пользователь для SSH
- `bootstrap_password` — пароль пользователя (используется только на этапе bootstrap)
- `bootstrap_public_key` — путь до публичного ключа (опционально, можно задать один раз)

**IP центрального менеджера** (`central_manager_ip`) подставляется автоматически из первого узла в группе `[central_manager]` (файл `scripts/group_vars/all.yml`). Вручную задавать его в `vars.yml` не нужно.

**Пароль пула HTCondor** не храните в репозитории. Задайте его переменной окружения перед запуском:
```bash
export CONDOR_CLUSTER_PASSWORD="ваш_секретный_пароль"
```
Без этой переменной плейбук `08_htcondor.yml` завершится с ошибкой.

Пример:
```ini
[bootstrap]
node1 ansible_host=192.168.56.107 ansible_user=vboxuser bootstrap_password=secret bootstrap_public_key=~/.ssh/id_ed25519.pub

[central_manager]
manager ansible_host=192.168.56.107 ansible_user=vboxuser ansible_ssh_private_key_file=~/.ssh/id_ed25519

[execute_nodes]
worker ansible_host=192.168.56.105 ansible_user=vboxuser ansible_ssh_private_key_file=~/.ssh/id_ed25519
```

### Запуск развертывания
1. Заполните `scripts/inventory.ini`.
2. Убедитесь, что плейбуки лежат в `scripts/`.
3. Запустите приложение:
   - из корня проекта: `java -jar target/MAS-1.0-SNAPSHOT.jar`
   - или используйте минимальный дистрибутив (см. «Минимальное развёртывание на узле»): скопируйте `target/mas-deploy/` на узел и запускайте `java -jar` из этой папки.
4. Приложение автоматически запускает `BootstrapAgent` для узлов из группы `[bootstrap]`.
5. После успешного bootstrap автоматически стартует `CoordinatorAgent`, который запускает плейбуки по этапам.

Если группа `[bootstrap]` отсутствует или пуста, координатор стартует сразу, без первичной настройки.

### Kubernetes kubeconfig
Плейбук `08_htcondor.yml` использует kubeconfig на центральном узле.
По умолчанию берётся `/etc/kubernetes/admin.conf`. Если у вас другой путь, задайте его в `scripts/vars.yml`:
```yaml
kubeconfig_path: "/path/to/your/kubeconfig"
```

### Установка зависимостей (Ubuntu/Debian)
```bash
# Java 17
sudo apt update && sudo apt install -y openjdk-17-jdk

# Ansible
sudo apt install -y ansible

# Проверка
java -version
ansible --version
```
# Поддерживаемые ОС (scripts/os/)

Конфигурация пакетов по семействам дистрибутивов.

## Семейства

| Файл | Семейство | Дистрибутивы |
|------|-----------|--------------|
| `debian.yml` | apt | Debian, Ubuntu, **AstraLinux** |
| `redhat.yml` | dnf | RHEL, Rocky, AlmaLinux, Fedora, **Red OS (Рэд ОС)** |

## AstraLinux

Основан на Debian. Использует `debian.yml` (apt, тот же набор пакетов).

## Red OS (Рэд ОС)

Основан на RHEL. Использует `redhat.yml` (dnf, conntrack-tools, container-selinux и др.).

## Добавление нового дистрибутива

1. Определите семейство: Debian-like (apt) или RedHat-like (dnf).
2. Если пакеты совпадают — дистрибутив подхватится через `ansible_os_family` или `ansible_distribution`.
3. Для особых пакетов — создайте `os/ИМЯ.yml` и добавьте логику в плейбуки.


### Частые ошибки
- `System has not been booted with systemd as init system` — узел без systemd (контейнер/WSL). Включите `mas.mode=existing-cluster` или исключите `02–07` через `mas.playbooks.skip`.
- `Inventory не найден` — проверьте `mas.paths.inventory` и наличие `scripts/inventory.ini`.
- `Playbook ... failed: CONNECTION_FAILURE` — проверьте SSH‑доступ, пользователей и ключи.
- Таймаут / `Connection timed out` к `prod-cdn.packages.k8s.io` при установке kubeadm/kubelet — на узле нет стабильного доступа к CDN Kubernetes. Рекомендуемый фикс: использовать зеркало `pkgs.k8s.io` от Яндекса (`k8s_pkg_mirror: "yandex"` в `scripts/vars.yml`).


