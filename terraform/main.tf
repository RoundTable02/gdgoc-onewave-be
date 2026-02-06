# =============================================================================
# Connectable Infrastructure - Main Configuration (Hackathon Edition)
# =============================================================================
# 
# This Terraform configuration provisions the following resources:
# - Compute Engine VM for Spring Boot API (e2-medium, ~$0.82/day)
# - GCS bucket for static file hosting (submitted files)
# - Secret Manager secrets for sensitive configuration
# - Artifact Registry for container images
# - Service accounts with minimal required permissions
#
# Budget target: < $10/day for 24-hour hackathon
# =============================================================================

# -----------------------------------------------------------------------------
# Provider Configuration
# -----------------------------------------------------------------------------

provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

# -----------------------------------------------------------------------------
# Data Sources
# -----------------------------------------------------------------------------

data "google_project" "current" {
  project_id = var.project_id
}

# -----------------------------------------------------------------------------
# Local Variables
# -----------------------------------------------------------------------------

locals {
  common_labels = merge(var.labels, {
    project    = "connectable"
    managed_by = "terraform"
  })

  # GCS base URL for static hosting
  gcs_base_url = "https://storage.googleapis.com"
}

# -----------------------------------------------------------------------------
# Enable Required APIs
# -----------------------------------------------------------------------------

resource "google_project_service" "required_apis" {
  for_each = toset([
    "compute.googleapis.com",
    "secretmanager.googleapis.com",
    "storage.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
  ])

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

# -----------------------------------------------------------------------------
# Secret Manager Secrets
# -----------------------------------------------------------------------------

locals {
  secret_names = [
    "supabase-host",
    "supabase-db",
    "supabase-user",
    "supabase-password",
    "gemini-api-key",
  ]
}

module "secrets" {
  source = "./modules/secrets"

  project_id = var.project_id
  region     = var.region
  labels     = local.common_labels

  secret_names = local.secret_names
  secret_values = {
    "supabase-host"     = var.supabase_host
    "supabase-db"       = var.supabase_db
    "supabase-user"     = var.supabase_user
    "supabase-password" = var.supabase_password
    "gemini-api-key"    = var.gemini_api_key
  }

  depends_on = [google_project_service.required_apis]
}

# -----------------------------------------------------------------------------
# Service Accounts & IAM
# -----------------------------------------------------------------------------

module "iam" {
  source = "./modules/iam"

  project_id     = var.project_id
  project_number = data.google_project.current.number
  labels         = local.common_labels

  secret_names = local.secret_names

  depends_on = [google_project_service.required_apis, module.secrets]
}

# -----------------------------------------------------------------------------
# Artifact Registry
# -----------------------------------------------------------------------------

resource "google_artifact_registry_repository" "connectable" {
  provider = google-beta

  location      = var.region
  repository_id = "connectable"
  description   = "Docker repository for Connectable API"
  format        = "DOCKER"

  cleanup_policies {
    id     = "delete-untagged"
    action = "DELETE"
    condition {
      tag_state  = "UNTAGGED"
      older_than = "604800s" # 7 days
    }
  }

  cleanup_policies {
    id     = "keep-minimum-versions"
    action = "KEEP"
    most_recent_versions {
      keep_count = 5
    }
  }

  labels = local.common_labels

  depends_on = [google_project_service.required_apis]
}

# Grant VM service account access to pull images
resource "google_artifact_registry_repository_iam_member" "vm_reader" {
  provider = google-beta

  project    = var.project_id
  location   = google_artifact_registry_repository.connectable.location
  repository = google_artifact_registry_repository.connectable.name
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${module.iam.service_account_email}"

  depends_on = [module.iam]
}

# -----------------------------------------------------------------------------
# GCS Bucket for Static Hosting
# -----------------------------------------------------------------------------

module "gcs" {
  source = "./modules/gcs"

  project_id           = var.project_id
  region               = var.region
  bucket_name          = var.gcs_bucket_name
  storage_class        = var.gcs_storage_class
  lifecycle_age_days   = var.gcs_lifecycle_age_days
  cors_allowed_origins = var.cors_allowed_origins
  labels               = local.common_labels

  depends_on = [google_project_service.required_apis]
}

# Grant VM service account access to write to GCS
resource "google_storage_bucket_iam_member" "vm_writer" {
  bucket = module.gcs.bucket_name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${module.iam.service_account_email}"

  depends_on = [module.gcs, module.iam]
}

# -----------------------------------------------------------------------------
# Cloud Run Worker (NestJS Playwright) - Deploy first to get URL
# -----------------------------------------------------------------------------

module "cloud_run_worker" {
  source = "./modules/cloud-run-worker"

  project_id = var.project_id
  region     = var.region
  labels     = local.common_labels

  service_name                  = "connectable-worker"
  container_image               = var.worker_image
  service_account_email         = module.iam.service_account_email
  invoker_service_account_email = module.iam.service_account_email

  min_instances   = var.worker_min_instances
  max_instances   = var.worker_max_instances
  cpu             = "1"
  memory          = "2Gi"
  timeout_seconds = 300

  depends_on = [
    google_project_service.required_apis,
    module.iam,
    google_artifact_registry_repository_iam_member.vm_reader,
  ]
}

# -----------------------------------------------------------------------------
# Compute Engine VM - Uses Cloud Run Worker URL
# -----------------------------------------------------------------------------

module "compute_engine" {
  source = "./modules/compute-engine"

  project_id = var.project_id
  region     = var.region
  zone       = var.zone
  labels     = local.common_labels

  instance_name         = var.instance_name
  machine_type          = var.machine_type
  service_account_email = module.iam.service_account_email
  container_image       = var.container_image
  worker_url            = module.cloud_run_worker.service_url

  env_vars = {
    SPRING_PROFILES_ACTIVE = "prod"
    GCS_BUCKET_NAME        = module.gcs.bucket_name
    GCS_BASE_URL           = local.gcs_base_url
    GEMINI_MODEL           = var.gemini_model
    WORKER_TIMEOUT_SECONDS = tostring(var.worker_timeout_seconds)
  }

  secret_env_vars = {
    SUPABASE_HOST     = "supabase-host"
    SUPABASE_DB       = "supabase-db"
    SUPABASE_USER     = "supabase-user"
    SUPABASE_PASSWORD = "supabase-password"
    GEMINI_API_KEY    = "gemini-api-key"
  }

  depends_on = [
    google_project_service.required_apis,
    module.secrets,
    module.iam,
    module.gcs,
    module.cloud_run_worker,
    google_artifact_registry_repository_iam_member.vm_reader,
  ]
}
