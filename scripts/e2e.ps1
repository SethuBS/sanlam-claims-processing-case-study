param(
    [switch]$SkipBuild,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$callbackSecret = if ($env:PAYMENT_CALLBACK_SECRET) {
    $env:PAYMENT_CALLBACK_SECRET
} else {
    'local-development-callback-secret'
}

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ($Expected -ne $Actual) {
        throw "$Message. Expected '$Expected' but got '$Actual'."
    }
}

function Wait-ForService {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        try {
            Invoke-WebRequest -UseBasicParsing `
                'http://localhost:8080/api/v1/claims/00000000-0000-0000-0000-000000000000' `
                -TimeoutSec 3 | Out-Null
            return
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
                return
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw 'Claims service did not become ready within 60 seconds.'
}

function Submit-Claim(
    [string]$Reference,
    [string]$Key,
    [string]$ClientId,
    [string]$PolicyNumber,
    [string]$ClaimType = 'STANDARD'
) {
    $body = @{
        externalReference = $Reference
        claimType = $ClaimType
        clientId = $ClientId
        policyNumber = $PolicyNumber
        incidentDate = '2026-08-31'
        amount = @{ currency = 'ZAR'; value = 1000.00 }
    } | ConvertTo-Json -Depth 4

    Invoke-RestMethod -Method Post `
        -Uri 'http://localhost:8080/api/v1/claims' `
        -Headers @{ 'Idempotency-Key' = $Key } `
        -ContentType 'application/json' `
        -Body $body
}

function Wait-ForClaimStatus([string]$ClaimId, [string[]]$Statuses) {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 500
        $claim = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/claims/$ClaimId"
    } while ($Statuses -notcontains $claim.status -and (Get-Date) -lt $deadline)
    return $claim
}

function Complete-Payment($Claim) {
    $callback = @{
        eventId = [Guid]::NewGuid()
        claimId = $Claim.claimId
        paymentReference = $Claim.paymentReference
        status = 'COMPLETED'
        occurredAt = (Get-Date).ToUniversalTime().ToString('o')
    } | ConvertTo-Json -Compress

    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [System.Text.Encoding]::UTF8.GetBytes($callbackSecret)
    )
    try {
        $signatureBytes = $hmac.ComputeHash(
            [System.Text.Encoding]::UTF8.GetBytes("$timestamp.$callback")
        )
        $signature = 'v1=' + [Convert]::ToHexString($signatureBytes).ToLowerInvariant()
    } finally {
        $hmac.Dispose()
    }

    Invoke-RestMethod -Method Post `
        -Uri 'http://localhost:8080/internal/v1/payment-status-events' `
        -Headers @{
            'X-Callback-Timestamp' = $timestamp
            'X-Callback-Signature' = $signature
        } `
        -ContentType 'application/json' `
        -Body $callback | Out-Null
}

Push-Location $repositoryRoot
try {
    if (-not $SkipBuild) {
        .\gradlew.bat clean test build
    }

    docker compose up -d --build
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }
    Wait-ForService
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)

    $straightThrough = Submit-Claim "WEB-E2E-$suffix" "e2e-$suffix" 'CLIENT-1' 'POL-1' 'DEATH'
    $pending = Wait-ForClaimStatus $straightThrough.claimId @('PAYMENT_PENDING', 'PAID')
    if ($pending.status -eq 'PAYMENT_PENDING') {
        Complete-Payment $pending
    }
    $paid = Wait-ForClaimStatus $pending.claimId @('PAID')
    Assert-Equal 'PAID' $paid.status 'Payment callback failed'

    $invalid = Submit-Claim "WEB-E2E-INVALID-$suffix" "e2e-invalid-$suffix" 'INVALID' 'POL-1'
    $invalidResult = Wait-ForClaimStatus $invalid.claimId @('REJECTED')
    Assert-Equal 'REJECTED' $invalidResult.status 'Invalid client was not rejected'

    $manual = Submit-Claim "WEB-E2E-MANUAL-$suffix" "e2e-manual-$suffix" 'CLIENT-1' 'MANUAL'
    $manualReview = Wait-ForClaimStatus $manual.claimId @('MANUAL_REVIEW')
    Assert-Equal 'MANUAL_REVIEW' $manualReview.status 'Manual review routing failed'
    $approved = Invoke-RestMethod -Method Post `
        -Uri "http://localhost:8080/api/v1/claims/$($manual.claimId)/decisions/approve"
    if ($approved.status -eq 'PAYMENT_PENDING') {
        Complete-Payment $approved
    }
    $manualPaid = Wait-ForClaimStatus $manual.claimId @('PAID')
    Assert-Equal 'PAID' $manualPaid.status 'Approved manual claim was not paid'

    $duplicateOne = Submit-Claim "WEB-E2E-DUP-$suffix" "e2e-dup-$suffix" 'CLIENT-1' 'POL-1'
    $duplicateTwo = Submit-Claim "WEB-E2E-DUP-$suffix" "e2e-dup-$suffix" 'CLIENT-1' 'POL-1'
    Assert-Equal $duplicateOne.claimId $duplicateTwo.claimId 'Duplicate submission created another claim'

    [pscustomobject]@{
        straightThrough = $paid.status
        invalidClient = $invalidResult.status
        manualReview = $manualPaid.status
        duplicateReturnedSameClaim = $true
    } | Format-List
} finally {
    if (-not $KeepRunning) {
        docker compose down
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "docker compose down failed with exit code $LASTEXITCODE"
        }
    }
    Pop-Location
}
