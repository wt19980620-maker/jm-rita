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
    $patterns = [ordered]@{
        '个人邮箱' = '(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}(?![A-Z0-9.-])'
        '私钥' = '-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
        'GitHub 令牌' = '(?i)gh[pousr]_[A-Za-z0-9_]{20,}'
        'GitHub fine-grained token' = 'github_pat_[A-Za-z0-9_]{20,}'
        'AWS 密钥' = 'AKIA[0-9A-Z]{16}'
        'Google API 密钥' = 'AIza[0-9A-Za-z_-]{35}'
        'JWT' = 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
        '硬编码凭据' = '(?i)(?:api[_-]?key|access[_-]?token|secret[_-]?key|client[_-]?secret|password)["'']?\s*[:=]\s*["''][^"'']{6,}["'']'
    }

    $findings = [System.Collections.Generic.List[string]]::new()

    function Test-ContentBytes([byte[]]$Bytes, [string]$Location) {
        if ($Location -match '(?i)\.pyc$') {
            # Python bytecode can retain ASCII credentials even though it is binary.
            $content = [System.Text.Encoding]::GetEncoding(28591).GetString($Bytes)
        } elseif ($Bytes.Length -ge 2 -and $Bytes[0] -eq 255 -and $Bytes[1] -eq 254) {
            $content = [System.Text.Encoding]::Unicode.GetString($Bytes)
        } elseif ($Bytes.Length -ge 2 -and $Bytes[0] -eq 254 -and $Bytes[1] -eq 255) {
            $content = [System.Text.Encoding]::BigEndianUnicode.GetString($Bytes)
        } else {
            if ($Bytes -contains 0) {
                if ($Location -notmatch '(?i)\.(jar|png|jpe?g|webp|gif|ico|ttf|otf|woff2?)$') {
                    $findings.Add("Binary or NUL data requires manual review: $Location")
                }
                return
            }
            try {
                $content = [System.Text.UTF8Encoding]::new($false, $true).GetString($Bytes)
            } catch [System.Text.DecoderFallbackException] {
                $findings.Add("Non-UTF text or binary requires manual review: $Location")
                return
            }
        }
        $lineNumber = 0
        foreach ($line in ($content -split "`r?`n")) {
            $lineNumber++
            foreach ($entry in $patterns.GetEnumerator()) {
                if ($line -match $entry.Value) {
                    $findings.Add("$($entry.Key): ${Location}:$lineNumber")
                }
            }
        }
    }

    foreach ($relativePath in $files) {
        $normalizedPath = $relativePath -replace '\\', '/'
        if ($normalizedPath -match $blockedPathPattern) {
            $findings.Add("禁止上传的文件: $normalizedPath")
            continue
        }

        if (Test-Path -LiteralPath $relativePath -PathType Leaf) {
            if ((Get-Item -LiteralPath $relativePath).Length -gt 16MB) {
                $findings.Add("Large file requires manual review: $normalizedPath")
            } else {
                Test-ContentBytes ([System.IO.File]::ReadAllBytes((Join-Path $repoRoot $relativePath))) $normalizedPath
            }
        }
    }

    # Scan Git objects, not only the current checkout: staged and older secrets are still pushed.
    $objects = @{}
    foreach ($entry in @(git -c core.quotepath=false ls-files --stage)) {
        if ($entry -match '^\d+ ([a-f0-9]+) \d+\t(.+)$') {
            $objects[$Matches[1]] = "index/$($Matches[2])"
        }
    }
    foreach ($entry in @(git -c core.quotepath=false rev-list --objects --all)) {
        if ($entry -match '^([a-f0-9]+) (.+)$') {
            $path = $Matches[2]
            $objects[$Matches[1]] = "history/$path"
            if ($path -match $blockedPathPattern) {
                $findings.Add("Forbidden file in Git history: $path")
            }
        }
    }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Git history' }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = (Get-Command git -CommandType Application | Select-Object -First 1).Source
    $process.StartInfo.Arguments = 'cat-file --batch'
    $process.StartInfo.WorkingDirectory = $repoRoot
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.RedirectStandardInput = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $null = $process.Start()
    $streamCompleted = $false
    try {
        $stream = $process.StandardOutput.BaseStream
        # .NET Framework may emit a BOM on redirected stdin. Consume its empty request first.
        $process.StandardInput.BaseStream.WriteByte(10)
        $process.StandardInput.BaseStream.Flush()
        do {
            $value = $stream.ReadByte()
            if ($value -lt 0) { throw 'Cannot initialize Git object stream' }
        } while ($value -ne 10)
        foreach ($objectId in $objects.Keys) {
            $requestBytes = [System.Text.Encoding]::ASCII.GetBytes("$objectId`n")
            $process.StandardInput.BaseStream.Write($requestBytes, 0, $requestBytes.Length)
            $process.StandardInput.BaseStream.Flush()
            $header = [System.Text.StringBuilder]::new()
            while (($value = $stream.ReadByte()) -ne 10) {
                if ($value -lt 0) { throw 'Unexpected end of Git object stream' }
                $null = $header.Append([char]$value)
            }
            if ($header.ToString() -notmatch '^[a-f0-9]+ (blob|tree|commit|tag) ([0-9]+)$') {
                throw "Cannot read Git object: $($header.ToString())"
            }
            $isBlob = $Matches[1] -eq 'blob'
            $size = [long]$Matches[2]
            $inspect = $isBlob -and $size -le 16MB
            if ($isBlob -and -not $inspect) {
                $findings.Add("Large Git object requires manual review: $($objects[$objectId])")
            }
            [byte[]]$bytes = [byte[]]::new(8192)
            if ($inspect) { $bytes = [byte[]]::new([int]$size) }
            $offset = 0L
            while ($offset -lt $size) {
                $count = $stream.Read($bytes, $(if ($inspect) { [int]$offset } else { 0 }),
                    [int][Math]::Min($size - $offset, $(if ($inspect) { $size - $offset } else { $bytes.Length })))
                if ($count -le 0) { throw 'Incomplete Git object' }
                $offset += $count
            }
            if ($stream.ReadByte() -ne 10) { throw 'Invalid Git object delimiter' }
            if ($inspect) { Test-ContentBytes $bytes $objects[$objectId] }
        }
        $streamCompleted = $true
    } finally {
        $process.StandardInput.BaseStream.Close()
        if (-not $streamCompleted -and -not $process.HasExited) { $process.Kill() }
        $process.WaitForExit()
        $process.Dispose()
    }

    $commitMessages = @(git log --all --format='%B') -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect commit messages' }
    Test-ContentBytes ([System.Text.Encoding]::UTF8.GetBytes($commitMessages)) 'history/commit-messages'

    $authorEmails = @()
    $commitCount = [int](git rev-list --all --count)
    if ($commitCount -gt 0) {
        $authorEmails = @(git log --all --format='%ae%n%ce' 2>$null | Sort-Object -Unique)
    }
    foreach ($authorEmail in $authorEmails) {
        if ($authorEmail -and $authorEmail -notmatch '(?i)^[^@\s]+@(users\.noreply\.github\.com|localhost|invalid)$') {
            $findings.Add('提交历史包含非 noreply 作者或提交者邮箱')
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
