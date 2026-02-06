# =============================================================================
# IAM Module
# =============================================================================
# Creates service accounts and IAM bindings with minimal required permissions
# =============================================================================

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "project_number" {
  description = "GCP Project Number"
  type        = string
}

variable "labels" {
  description = "Labels to apply to resources"
  type        = map(string)
  default     = {}
}

variable "secret_names" {
  description = "List of secret names for IAM binding"
  type        = list(string)
}

# -----------------------------------------------------------------------------
# VM Service Account
# -----------------------------------------------------------------------------

resource "google_service_account" "vm" {
  project      = var.project_id
  account_id   = "connectable-api"
  display_name = "Connectable API Service Account"
  description  = "Service account for Connectable Spring Boot API running on Compute Engine"
}

# -----------------------------------------------------------------------------
# Secret Manager Access
# Grant VM service account access to read secrets
# -----------------------------------------------------------------------------

resource "google_secret_manager_secret_iam_member" "vm_secret_accessor" {
  for_each = toset(var.secret_names)

  project   = var.project_id
  secret_id = "connectable-${each.key}"
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.vm.email}"
}

# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

output "service_account_email" {
  description = "Email of the VM service account"
  value       = google_service_account.vm.email
}

output "service_account_name" {
  description = "Name of the VM service account"
  value       = google_service_account.vm.name
}
