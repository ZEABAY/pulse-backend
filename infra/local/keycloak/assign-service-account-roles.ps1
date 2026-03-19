# Assigns realm-management roles (manage-users, view-users) to pulse-backend-client's service account.
# Run once after Keycloak is up and realm "pulse" has been imported.
# Usage: .\assign-service-account-roles.ps1 [-KeycloakUrl "http://localhost:9080"] [-AdminUser "admin"] [-AdminPassword "admin"]

param(
    [string]$KeycloakUrl = "http://localhost:9080",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin"
)

$ErrorActionPreference = "Stop"
$REALM = "pulse"
$CLIENT_ID = "pulse-backend-client"

# Keycloak 26.x health/ready endpoint is on the management port (9000), not the main port (8080).
# docker-compose maps management port 9000 -> host 9090.
$healthUrl = "http://localhost:9090/health/ready"

Write-Host "Waiting for Keycloak at $healthUrl ..."
do {
    try {
        $null = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2
        break
    } catch {
        Start-Sleep -Seconds 2
    }
} while ($true)
Write-Host "Keycloak is ready."

$tokenBody = @{
    grant_type = "password"
    client_id  = "admin-cli"
    username   = $AdminUser
    password   = $AdminPassword
}
$tokenResponse = Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body $tokenBody

$token = $tokenResponse.access_token
if (-not $token) {
    Write-Error "Failed to get admin token. Check admin credentials and Keycloak URL."
}

$headers = @{ "Authorization" = "Bearer $token" }

Write-Host "Getting $CLIENT_ID client id..."
$clients = Invoke-RestMethod -Method Get -Uri "$KeycloakUrl/admin/realms/$REALM/clients?clientId=$CLIENT_ID" -Headers $headers
$backendClientUuid = $clients[0].id
if (-not $backendClientUuid) {
    Write-Error "Client $CLIENT_ID not found in realm $REALM. Ensure realm has been imported (e.g. start Keycloak with --import-realm)."
}

Write-Host "Getting service account user for $CLIENT_ID..."
$serviceAccountUser = Invoke-RestMethod -Method Get -Uri "$KeycloakUrl/admin/realms/$REALM/clients/$backendClientUuid/service-account-user" -Headers $headers
$userId = $serviceAccountUser.id
if (-not $userId) {
    Write-Error "Service account user not found for $CLIENT_ID."
}

Write-Host "Getting realm-management client id..."
$realmMgmtClients = Invoke-RestMethod -Method Get -Uri "$KeycloakUrl/admin/realms/$REALM/clients?clientId=realm-management" -Headers $headers
$realmMgmtId = $realmMgmtClients[0].id
if (-not $realmMgmtId) {
    Write-Error "realm-management client not found."
}

Write-Host "Getting realm-management roles (manage-users, view-users)..."
$allRoles = Invoke-RestMethod -Method Get -Uri "$KeycloakUrl/admin/realms/$REALM/clients/$realmMgmtId/roles" -Headers $headers
$payload = @($allRoles | Where-Object { $_.name -eq "manage-users" -or $_.name -eq "view-users" })
if ($payload.Count -eq 0) {
    Write-Error "Could not find manage-users and view-users roles in realm-management."
}
$payloadJson = $payload | ConvertTo-Json -Depth 10

Write-Host "Assigning manage-users and view-users to service account..."
try {
    Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/admin/realms/$REALM/users/$userId/role-mappings/clients/$realmMgmtId" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $payloadJson
    Write-Host "Roles assigned successfully. auth-service can now create users in Keycloak."
} catch {
    Write-Error "Failed to assign roles. HTTP $($_.Exception.Response.StatusCode.Value__)"
}

# User Profile: firstName / lastName opsiyonel (profil verisi user-profile-service'te tutulacak)
Write-Host "Making firstName and lastName optional in user profile..."
try {
    $profile = Invoke-RestMethod -Method Get -Uri "$KeycloakUrl/admin/realms/$REALM/users/profile" -Headers $headers
    foreach ($attr in $profile.attributes) {
        if ($attr.name -in @("firstName", "lastName") -and $attr.PSObject.Properties["required"]) {
            $attr.PSObject.Properties.Remove("required")
        }
    }
    $profileJson = $profile | ConvertTo-Json -Depth 20 -Compress
    Invoke-RestMethod -Method Put -Uri "$KeycloakUrl/admin/realms/$REALM/users/profile" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $profileJson | Out-Null
    Write-Host "User profile updated: firstName and lastName are now optional."
} catch {
    Write-Warning "Failed to update user profile. $($_.Exception.Message)"
}
