Of course. Here is a consolidated summary table of all the fields from the Protocol Buffer definition, organized by their parent message for clarity.

### **Universal Asset Field Summary**

This table provides a comprehensive overview of every field within the `UniversalAsset` protocol buffer schema.

| Field Name | Parent Message | Data Type | Requirement | Description |
| :--- | :--- | :--- | :--- | :--- |
| **---** | **`RequiredMetadata`** | **---** | **Required** | **---** |
| `id` | `RequiredMetadata` | `string` | Required | Immutable, unique system identifier (UUID) for the asset. |
| `title` | `RequiredMetadata` | `string` | Required | Primary human-readable name of the asset. |
| `asset_type` | `RequiredMetadata` | `enum AssetType` | Required | The general type of the asset (e.g., `DOCUMENT`, `DATASET`). |
| `owner_group_id`| `RequiredMetadata` | `string` | Required | Identifier for the group/department responsible for the asset. |
| `classification`| `RequiredMetadata` | `enum ClassificationLevel` | Required | The security classification level (e.g., `U`, `CUI`, `S`). |
| `created_at` | `RequiredMetadata` | `google.protobuf.Timestamp`| Required | Timestamp of the original asset creation. |
| `updated_at` | `RequiredMetadata` | `google.protobuf.Timestamp`| Required | Timestamp of the asset's last modification. |
| `is_searchable`| `RequiredMetadata` | `bool` | Required | Master switch to include or exclude the asset from all search queries. |
| **---** | **`RecommendedMetadata`** | **---** | **Recommended** | **---** |
| `summary` | `RecommendedMetadata` | `string` | Recommended | A brief, one-paragraph abstract of the asset's content. |
| `description` | `RecommendedMetadata` | `Text` | Recommended | A detailed, comprehensive description of the asset. |
| `tags` | `RecommendedMetadata` | `repeated string` | Recommended | A list of informal, user-defined keywords for discovery. |
| `categories` | `RecommendedMetadata` | `repeated string` | Recommended | A list of formal, structured categories the asset belongs to. |
| `status` | `RecommendedMetadata` | `enum LifecycleStatus` | Recommended | The current lifecycle state of the asset (e.g., `DRAFT`, `APPROVED`). |
| `version_id` | `RecommendedMetadata` | `string` | Recommended | The specific version of the asset (e.g., `1.3.2`, `2025-Q3-Final`). |
| **---** | **`SecurityAndProvenance`** | **---** | **Optional** | **---** |
| `access_control_list`| `SecurityAndProvenance` | `repeated AccessControlEntry`| Optional | List of specific users/groups and their permission levels. |
| `caveats` | `SecurityAndProvenance` | `repeated enum Caveat` | Optional | Additional handling instructions beyond classification (e.g., `NOFORN`). |
| `source` | `SecurityAndProvenance` | `string` | Optional | The originating system, person, or organization. |
| `author_list` | `SecurityAndProvenance` | `repeated string` | Optional | The list of individuals who authored the asset's content. |
| `doi` | `SecurityAndProvenance` | `string` | Optional | The Digital Object Identifier for the asset, if applicable. |
| `related_assets`| `SecurityAndProvenance` | `repeated string` | Optional | A list of `id`s for other assets related to this one. |
| `retention_policy_id` | `SecurityAndProvenance` | `string` | Optional | Identifier for the official records retention policy. |
| **---** | **`LocationAndCustomMetadata`** | **---** | **Optional** | **---** |
| `location_uri` | `LocationAndCustomMetadata`| `string` (URI) | Optional | The Uniform Resource Identifier pointing to the actual asset content. |
| `location_type` | `LocationAndCustomMetadata`| `enum LocationType` | Optional | The type of the storage location (e.g., `FILE_SYSTEM`, `S3`). |
| `mime_type` | `LocationAndCustomMetadata`| `string` | Optional | The specific file type of the asset (e.g., `application/pdf`). |
| `custom_metadata`| `LocationAndCustomMetadata`| `google.protobuf.Struct` | Optional | Flexible key-value store for department-specific metadata. |