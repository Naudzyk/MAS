terraform {
  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "2.4.0"
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

  # Нормализуем узлы, гарантируя, что все поля имеют строковые значения (не null)
  normalized_nodes = [
    for node in local.valid_nodes : {
      inventory_name = try(node.inventory_name, null) != null ? tostring(node.inventory_name) : ""
      ip_address     = try(node.ip_address, null) != null ? tostring(node.ip_address) : ""
      ssh_user       = try(node.ssh_user, null) != null ? tostring(node.ssh_user) : ""
      ssh_key_path   = try(node.ssh_key_path, null) != null && node.ssh_key_path != "" ? tostring(node.ssh_key_path) : ""
      group          = try(node.group, null) != null ? tostring(node.group) : ""
    }
  ]

  groups = distinct([for node in local.normalized_nodes : node.group])

  grouped_nodes = {
    for group in local.groups : group => [
      for node in local.normalized_nodes : node if node.group == group
    ]
  }
}

resource "local_file" "inventory" {
  content  = templatefile("${path.module}/templates/inventory.tpl", {
    groups = local.grouped_nodes
  })
  filename = "${path.root}/../scripts/inventory.ini"
}

