# =============================================================================
# Secret Manager Module
# =============================================================================
# Creates and manages secrets in Google Secret Manager
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP Region"
  type        = string
}

variable "labels" {
  description = "Labels to apply to secrets"
  type        = map(string)
  default     = {}
}

# Secret names (non-sensitive, used for for_each)
variable "secret_names" {
  description = "List of secret names to create"
  type        = list(string)
}

# Secret values (sensitive, indexed by name)
variable "secret_values" {
  description = "Map of secret names to their values"
  type        = map(string)
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Secrets
# -----------------------------------------------------------------------------

resource "google_secret_manager_secret" "secrets" {
  for_each = toset(var.secret_names)

  project   = var.project_id
  secret_id = "connectable-${each.key}"

  labels = var.labels

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "versions" {
  for_each = toset(var.secret_names)

  secret      = google_secret_manager_secret.secrets[each.key].id
  secret_data = var.secret_values[each.key]

  lifecycle {
    # Prevent unnecessary recreation when secret data changes
    # Update should create new version, not destroy/recreate
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

output "secret_ids" {
  description = "Map of secret names to their full secret IDs"
  value = {
    for key, secret in google_secret_manager_secret.secrets : key => secret.secret_id
  }
}

output "secret_names" {
  description = "Map of secret names to their resource names"
  value = {
    for key, secret in google_secret_manager_secret.secrets : key => secret.name
  }
}
