param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Output = 'docs/api/openapi.json'
)

Invoke-WebRequest -Uri "$BaseUrl/v3/api-docs" -UseBasicParsing -OutFile $Output
Write-Host "OpenAPI spec saved: $Output"

