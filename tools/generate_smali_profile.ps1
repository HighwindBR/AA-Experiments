param(
    [Parameter(Mandatory=$true)][string]$SmaliRoot,
    [Parameter(Mandatory=$true)][string]$BaseApk,
    [Parameter(Mandatory=$true)][string]$Output
)

$found = @{}
$ambiguous = @{}
$classPattern = [regex]::new('(?m)^\.class .*? L([^;]+);')
$flagPattern = [regex]::new('const-string\s+\S+,\s+"([A-Za-z0-9_]+__[^\"]+)"')

$candidateFiles = & rg --files-with-matches --glob '*.smali' '__' $SmaliRoot
foreach ($candidatePath in $candidateFiles) {
    $item = Get-Item -LiteralPath $candidatePath
    $text = [IO.File]::ReadAllText($item.FullName)
    $classMatch = $classPattern.Match($text)
    if (-not $classMatch.Success) { return }
    $className = $classMatch.Groups[1].Value.Replace('/', '.')
    $methodName = $null
    foreach ($line in [IO.File]::ReadLines($item.FullName)) {
        if ($line.StartsWith('.method ')) {
            $methodName = if ($line -match '([\w$<>]+)\(\)Z$') { $Matches[1] } else { $null }
            continue
        }
        if ($line.StartsWith('.end method')) { $methodName = $null; continue }
        if ($null -eq $methodName -or $line -notlike '*__*') { continue }
        foreach ($flag in $flagPattern.Matches($line)) {
            $key = $flag.Groups[1].Value
            $candidate = [ordered]@{ className=$className; methodName=$methodName; descriptor='()Z'; source=$item.FullName }
            if ($ambiguous.ContainsKey($key)) { $ambiguous[$key] += @($candidate) }
            elseif ($found.ContainsKey($key)) { $ambiguous[$key] = @($found[$key], $candidate); $found.Remove($key) }
            else { $found[$key] = $candidate }
        }
    }
}

$result = [ordered]@{
    schemaVersion = 1
    baseSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaseApk).Hash.ToLowerInvariant()
    getters = $found
    ambiguous = $ambiguous
}
$parent = Split-Path -Parent $Output
if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Output -Encoding utf8NoBOM
Write-Output "Generated $($found.Count) unique boolean getters; $($ambiguous.Count) ambiguous keys."
