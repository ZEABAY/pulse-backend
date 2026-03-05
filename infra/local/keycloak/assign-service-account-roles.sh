#!/usr/bin/env bash
# Assigns realm-management roles (manage-users, view-users) to pulse-backend-client's service account.
# Run once after Keycloak is up and realm "pulse" has been imported (e.g. via --import-realm).
# Usage: ./assign-service-account-roles.sh [KEYCLOAK_URL] [ADMIN_USER] [ADMIN_PASSWORD]

set -e

KEYCLOAK_URL="${1:-http://localhost:9080}"
ADMIN_USER="${2:-admin}"
ADMIN_PASSWORD="${3:-admin}"
REALM="pulse"
CLIENT_ID="pulse-backend-client"

# Keycloak 26.x health/ready endpoint is on the management port (9000), not the main port (8080).
# docker-compose maps management port 9000 -> host 9090.
KEYCLOAK_HEALTH_URL="http://localhost:9090"

echo "Waiting for Keycloak at $KEYCLOAK_HEALTH_URL/health/ready ..."
until curl -sf -o /dev/null "$KEYCLOAK_HEALTH_URL/health/ready"; do
  sleep 2
done
echo "Keycloak is ready."

TOKEN=$(curl -sS -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASSWORD" \
  | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Failed to get admin token. Check admin credentials and Keycloak URL."
  exit 1
fi

echo "Getting $CLIENT_ID client id..."
BACKEND_CLIENT_UUID=$(curl -sS -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  | jq -r '.[0].id')

if [ -z "$BACKEND_CLIENT_UUID" ] || [ "$BACKEND_CLIENT_UUID" = "null" ]; then
  echo "Client $CLIENT_ID not found in realm $REALM. Ensure realm has been imported (e.g. start Keycloak with --import-realm)."
  exit 1
fi

echo "Getting service account user for $CLIENT_ID..."
SERVICE_ACCOUNT_USER=$(curl -sS -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients/$BACKEND_CLIENT_UUID/service-account-user" \
  -H "Authorization: Bearer $TOKEN")
USER_ID=$(echo "$SERVICE_ACCOUNT_USER" | jq -r '.id')

if [ -z "$USER_ID" ] || [ "$USER_ID" = "null" ]; then
  echo "Service account user not found for $CLIENT_ID."
  exit 1
fi

echo "Getting realm-management client id..."
REALM_MGMT_ID=$(curl -sS -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=realm-management" \
  -H "Authorization: Bearer $TOKEN" \
  | jq -r '.[0].id')

if [ -z "$REALM_MGMT_ID" ] || [ "$REALM_MGMT_ID" = "null" ]; then
  echo "realm-management client not found."
  exit 1
fi

echo "Getting realm-management roles (manage-users, view-users)..."
ALL_ROLES=$(curl -sS -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients/$REALM_MGMT_ID/roles" \
  -H "Authorization: Bearer $TOKEN")
PAYLOAD=$(echo "$ALL_ROLES" | jq -c '[.[] | select(.name == "manage-users" or .name == "view-users")]')
if [ "$PAYLOAD" = "[]" ] || [ -z "$PAYLOAD" ]; then
  echo "Could not find manage-users and view-users roles in realm-management."
  exit 1
fi

echo "Assigning manage-users and view-users to service account..."
HTTP_CODE=$(curl -sS -o /tmp/assign-out -w "%{http_code}" -X POST \
  "$KEYCLOAK_URL/admin/realms/$REALM/users/$USER_ID/role-mappings/clients/$REALM_MGMT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
  echo "Roles assigned successfully. auth-service can now create users in Keycloak."
else
  echo "Failed to assign roles. HTTP $HTTP_CODE"
  cat /tmp/assign-out 2>/dev/null | jq . 2>/dev/null || cat /tmp/assign-out
  exit 1
fi

# ── User Profile: firstName / lastName NOT required ──────────────────────────
# Keycloak 26 declarative user profile requires firstName+lastName by default.
# In Pulse, profile data belongs to user-profile-service, not auth-service.
# We remove the 'required' constraint so registration only needs username+email+password.
echo "Removing required constraint from firstName and lastName in user profile..."
CURRENT_PROFILE=$(curl -sS "$KEYCLOAK_URL/admin/realms/$REALM/users/profile" \
  -H "Authorization: Bearer $TOKEN")

UPDATED_PROFILE=$(echo "$CURRENT_PROFILE" | python3 -c "
import sys, json
p = json.load(sys.stdin)
for attr in p.get('attributes', []):
    if attr.get('name') in ('firstName', 'lastName'):
        attr.pop('required', None)
print(json.dumps(p))
")

PROFILE_HTTP=$(curl -sS -o /tmp/profile-out -w "%{http_code}" -X PUT \
  "$KEYCLOAK_URL/admin/realms/$REALM/users/profile" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$UPDATED_PROFILE")

if [ "$PROFILE_HTTP" -ge 200 ] && [ "$PROFILE_HTTP" -lt 300 ]; then
  echo "User profile updated: firstName and lastName are now optional."
else
  echo "Warning: Failed to update user profile. HTTP $PROFILE_HTTP"
  cat /tmp/profile-out 2>/dev/null
fi
