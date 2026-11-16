$eventos = Get-ChildItem -Path (Join-Path $PSScriptRoot 'data') -Filter 'eventos-agencia-*.jsonl' -ErrorAction SilentlyContinue |
  ForEach-Object { Get-Content $_.FullName | ForEach-Object { $_ | ConvertFrom-Json } } |
  Where-Object { $_.tipo -in @('ELEICAO_INICIADA','ELEICAO_RECEBIDA','TORNOU_LIDER','LIDER_ANUNCIADO','LIDER_NAO_RESPONDEU') } |
  Sort-Object { [DateTime]$_.horaParede }

Write-Output '=== Historico de eventos de eleicao ==='
$eventos | ForEach-Object { "($($_.horaParede)) [$($_.agencia)] $($_.tipo) $($_.detalhes | ConvertTo-Json -Compress)" }
Write-Output "`n=== Linha do tempo de lideres ==="
$eventos | Where-Object tipo -eq 'TORNOU_LIDER' | ForEach-Object { "A partir de $($_.horaParede): Agencia $($_.detalhes.idAgencia) e lider" }
