# DEPRECATED: локальные `.deb` для Kubernetes

Режим офлайн-установки из локальных `.deb` больше не используется.

Для установки Kubernetes используйте **зеркало Яндекса**:
- выставьте `k8s_pkg_mirror: "yandex"` в `scripts/vars.yml`
- плейбук `03_kubernetes_install.yml` будет брать `kubeadm/kubelet/kubectl` из `mirror.yandex.ru/mirrors/pkgs.k8s.io`.
