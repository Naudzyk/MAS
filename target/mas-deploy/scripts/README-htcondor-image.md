# Образ HTCondor без доступа к Docker Hub

Поды тянут `condor_image` (по умолчанию `docker.io/evgenii457/htcondor2:latest`). Если с нод нет нормального доступа к Docker Hub, используйте один из вариантов.

## 1. VPN на всех нодах на время первого pull

После успешной загрузки образа на ноду при `imagePullPolicy: IfNotPresent` повторные старты не будут каждый раз ходить в registry.

## 2. Перенос образа tar (офлайн / один узел с VPN)

На машине **с VPN** (или где Hub доступен), с Docker:

```bash
docker pull docker.io/evgenii457/htcondor2:latest
docker save docker.io/evgenii457/htcondor2:latest -o htcondor2.tar
```

Скопируйте `htcondor2.tar` на **каждую** ноду кластера (mas, execute, kuber2) и выполните:

```bash
sudo ctr -n k8s.io images import htcondor2.tar
```

Проверка:

```bash
sudo ctr -n k8s.io images ls | grep htcondor
```

Затем перезапустите поды:

```bash
sudo kubectl -n default delete pod -l app=htcondor --force --grace-period=0
```

В `vars.yml` должны совпадать имя и тег образа с тем, что в tar (см. `condor_image`).

## 3. Свой registry (Aliyun ACR, GHCR и т.д.)

Запушьте образ под своим URL и в `vars.yml` укажите, например:

```yaml
condor_image: "registry.cn-hangzhou.aliyuncs.com/<namespace>/htcondor2:latest"
```

---

См. также `condor_image_pull_policy` в `vars.yml`.
