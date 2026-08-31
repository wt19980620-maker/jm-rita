param(
    [switch]$TrackedOnly
)

$ErrorActionPreference = "Stop"

git rev-parse --is-inside-work-tree 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "当前目录不是 Git 仓库。"
}

$repoRoot = git rev-parse --show-toplevel
Push-Location $repoRoot

try {
    $files = if ($TrackedOnly) {
        @(git -c core.quotepath=false ls-files)
    } else {
        @(git -c core.quotepath=false ls-files --cached --others --exclude-standard)
    }

    $blockedPathPattern = '(?i)(^|/)(local\.properties|\.env(?:\..*)?|release-key(?:/.*)?|[^/]+\.(?:jks|keystore|p12|pfx|pem|key))$'
    $textExtensions = @(
        '.gradle', '.java', '.json', '.kts', '.kt', '.md', '.properties',
        '.pro', '.ps1', '.sh', '.toml', '.txt', '.xml', '.yaml', '.yml'
    )
    $patterns = [ordered]@{
        '个人邮箱' = '(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}(?![A-Z0-9.-])'
        '私钥' = '-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
        'GitHub 令牌' = '(?i)gh[pousr]_[A-Za-z0-9_]{20,}'
        'AWS 密钥' = 'AKIA[0-9A-Z]{16}'
        'Google API 密钥' = 'AIza[0-9A-Za-z_-]{35}'
        'JWT' = 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
        '硬编码凭据' = '(?i)(?:api[_-]?key|access[_-]?token|secret[_-]?key|client[_-]?secret|password)\s*[:=]\s*["''][^"'']{6,}["'']'
    }

    $findings = [System.Collections.Generic.List[string]]::new()

    foreach ($relativePath in $files) {
        $normalizedPath = $relativePath -replace '\\', '/'
        if ($normalizedPath -match $blockedPathPattern) {
            $findings.Add("禁止上传的文件: $normalizedPath")
            continue
        }

        $extension = [System.IO.Path]::GetExtension($relativePath).ToLowerInvariant()
        if ($textExtensions -notcontains $extension -and $relativePath -notin @('.gitignore', 'gradlew')) {
            continue
        }

        $lineNumber = 0
        foreach ($line in Get-Content -LiteralPath $relativePath -ErrorAction Stop) {
            $lineNumber++
            foreach ($entry in $patterns.GetEnumerator()) {
                if ($line -match $entry.Value) {
                    $findings.Add("$($entry.Key): ${normalizedPath}:$lineNumber")
                }
            }
        }
    }

    $authorEmails = @()
    $commitCount = [int](git rev-list --all --count)
    if ($commitCount -gt 0) {
        $authorEmails = @(git log --format='%ae' 2>$null | Sort-Object -Unique)
    }
    foreach ($authorEmail in $authorEmails) {
        if ($authorEmail -and $authorEmail -notmatch '(?i)(users\.noreply\.github\.com|@localhost$|@invalid$)') {
            $findings.Add('提交历史包含非 noreply 作者邮箱')
            break
        }
    }

    if ($findings.Count -gt 0) {
        Write-Error ("上传前隐私审查失败：`n- " + ($findings -join "`n- "))
        exit 1
    }

    Write-Output "上传前隐私审查通过：未发现邮箱、账号凭据、API 密钥、令牌、私钥或禁止上传的本地文件。"
} finally {
    Pop-Location
}
