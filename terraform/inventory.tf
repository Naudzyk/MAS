terraform {
  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "2.4.0"
    }
    template = {
      source  = "hashicorp/template"
      version = "2.2.0"
    }
  }
}

data "local_file" "nodes_config" {
  filename = "${path.module}/inventory.yaml"
}

locals {
  config = yamldecode(data.local_file.nodes_config.content)
}

data "template_file" "inventory" {
  template = file("${path.module}/templates/inventory.tpl")
  vars = {
    masters = [for node in local.config.nodes : node.ip if node.role == "central_manager"]
    workers = [for node in local.config.nodes : node.ip if node.role == "execute_nodes"]
  }
}

resource "local_file" "inventory" {
  content  = data.template_file.inventory.rendered
  filename = "${path.root}/inventory.ini"
}