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

  nodes = try(local.config.nodes, [])

  valid_nodes = [for node in local.nodes : node if try(node.group, null) != null]

  groups = distinct([for node in local.valid_nodes : node.group])

  grouped_nodes = {
    for group in local.groups : group => [
      for node in local.valid_nodes : node if node.group == group
    ]
  }
}

data "template_file" "inventory" {
  template = file("${path.module}/templates/inventory.tpl")
  vars = {
    groups = local.grouped_nodes
  }
}

resource "local_file" "inventory" {
  content  = data.template_file.inventory.rendered
  filename = "${path.root}/../inventory.ini"
}

