variable "node_ip" {
  description = "IP-адрес существующей машины"
  type        = string
  default     = "192.168.56.107"
}

variable "ansible_user" {
  description = "Пользователь для подключения по SSH"
  type        = string
  default     = "vboxuser"
}

variable "ssh_key_path" {
  description = "Путь к SSH-ключу"
  type        = string
  default     = "~/.ssh/id_ed25519"
}