# Notification Event Matrix

## Scope

Current matrix for in-app notifications (`t_notification`) after full station-inbox enhancement.

## Events

| Event Code | Trigger Operation | Receiver | Source Type | Source Id | Notes |
| --- | --- | --- | --- | --- | --- |
| `onboarding_submitted` | Developer onboarding submit | `platform_admin` users | `developer_application` | application id | New applicant info + reason |
| `onboarding_approved` | Developer onboarding approve | applicant user | `developer_application` | application id | Reviewer + comment |
| `onboarding_rejected` | Developer onboarding reject | applicant user | `developer_application` | application id | Reviewer + reject reason |
| `grant_application_new` | Grant application submit | `platform_admin` users | `grant_application` | application id | Resource + api key + use case |
| `grant_approved` | Grant application approved | applicant user | `grant_application` | application id | Reviewer + resource |
| `grant_rejected` | Grant application rejected | applicant user | `grant_application` | application id | Reject reason |
| `resource_grant_updated` | Direct resource grant create/update | `platform_admin` users | `resource` | resource id | Operator + api key |
| `resource_grant_revoked` | Direct resource grant revoke | `platform_admin` users | `resource` | resource id | Operator + api key |
| `resource_submitted` | Resource submit for audit | dept admins | `resource` | resource id | Existing path retained |
| `resource_deprecated` | Resource deprecate | `platform_admin` users | `resource` | resource id | High-risk governance action |
| `resource_withdrawn` | Resource withdraw | `platform_admin` users | `resource` | resource id | Back to draft |
| `resource_version_switched` | Resource switch version | `platform_admin` users | `resource` | resource id | New current version |
| `audit_approved` | Audit approved | submitter | target resource type | target resource id | Existing audit flow |
| `audit_rejected` | Audit rejected | submitter | target resource type | target resource id | Existing audit flow |
| `resource_published` | Audit published | submitter | target resource type | target resource id | Existing audit flow |
| `password_changed` | User changes password | current user | `user` | user id | Security-sensitive |
| `phone_bound` | User binds phone | current user | `user` | user id | Security-sensitive |
| `session_killed` | User kills session | current user | `session` | session id | Session security |
| `api_key_created` | API key created | owner user (or platform owner id `0`) | `api_key` | key id | Key metadata notice |
| `api_key_revoked` | API key revoked/deleted | owner user (or platform owner id `0`) | `api_key` | key id | Sensitive credential action |
| `user_status_changed` | Admin updates user status | target user | `user` | user id | Lock/disable-like state updates |
| `user_deleted` | Admin deletes user | `platform_admin` users | `user` | user id | High-risk operation |
| `role_changed` | Role create/update/delete | `platform_admin` users | `role` | role id | Permission surface changed |
| `system_param_changed` | System param upsert | `platform_admin` users | `system-config` | - | High-risk config |
| `security_setting_changed` | Security setting upsert | `platform_admin` users | `system-config` | - | High-risk config |
| `system_network_applied` | Apply network policy | `platform_admin` users | `system-config` | - | High-risk config |
| `system_acl_published` | Publish ACL | `platform_admin` users | `system-config` | - | High-risk config |
| `alert` | Alert rule fired | system inbox user `0` | `alert` | alert record id | Rule/metric/threshold/sample |

## Message Content Structure

Most newly added notifications follow this structure:

- event
- result
- timestamp
- details block (operator, object, key fields)
- next action suggestion

This keeps message readability consistent while preserving `sourceType/sourceId` for UI deep-linking.
