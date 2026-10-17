$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$auditScript = Join-Path $PSScriptRoot 'audit-before-push.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('jm-security-audit-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $testRoot
$encoding = [System.Text.UTF8Encoding]::new($false)
$tokenFixture = ('gh' + 'p_' + ('x' * 30))
$testCount = 0

function Assert-Audit([bool]$ShouldPass) {
    $taskOldErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $auditScript -TrackedOnly 2>&1
    $passed = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $taskOldErrorPreference
    if ($passed -ne $ShouldPass) { throw "Unexpected audit result: $output" }
    $script:testCount++
}

function New-FixtureRepo([string]$Name) {
    $path = Join-Path $testRoot $Name
    $null = New-Item -ItemType Directory -Path $path
    Set-Location $path
    git init -q -b main
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create audit fixture' }
}

Push-Location
try {
    New-FixtureRepo 'text-extensions'
    foreach ($extension in @('py', 'js', 'html', 'cjs', 'bat', 'unknown')) {
        $path = Join-Path (Get-Location) ("fixture.$extension")
        [System.IO.File]::WriteAllText($path, $tokenFixture, $encoding)
        git add -- ("fixture.$extension")
        Assert-Audit $false
        [System.IO.File]::WriteAllText($path, 'clean fixture', $encoding)
        # A clean working copy must not hide the staged secret.
        Assert-Audit $false
        git add -- ("fixture.$extension")
        Assert-Audit $true
    }

    New-FixtureRepo 'historical-secret'
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.py'), $tokenFixture, $encoding)
    git add fixture.py
    $tree = git write-tree
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fixture tree' }
    $commit = git -c user.name=Audit -c user.email=noreply@localhost commit-tree $tree -m fixture
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create fixture history' }
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.py'), 'clean fixture', $encoding)
    git add fixture.py
    $cleanTree = git write-tree
    $cleanCommit = git -c user.name=Audit -c user.email=noreply@localhost commit-tree $cleanTree -p $commit -m clean
    git update-ref refs/heads/main $cleanCommit
    Assert-Audit $false

    New-FixtureRepo 'committer-email'
    $taskOldCommitterEmail = $env:GIT_COMMITTER_EMAIL
    try {
        $env:GIT_COMMITTER_EMAIL = 'fixture' + '@' + 'example.test'
        [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.py'), 'clean fixture', $encoding)
        git add fixture.py
        $tree = git write-tree
        $commit = git -c user.name=Audit -c user.email=noreply@localhost commit-tree $tree -m fixture
        git update-ref refs/heads/main $commit
    } finally {
        $env:GIT_COMMITTER_EMAIL = $taskOldCommitterEmail
    }
    Assert-Audit $false

    New-FixtureRepo 'commit-message'
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.txt'), 'clean fixture', $encoding)
    git add fixture.txt
    $tree = git write-tree
    $commit = git -c user.name=Audit -c user.email=noreply@localhost commit-tree $tree -m $tokenFixture
    git update-ref refs/heads/main $commit
    Assert-Audit $false

    New-FixtureRepo 'utf16-secret'
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.py'), $tokenFixture, [System.Text.Encoding]::Unicode)
    git add fixture.py
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.py'), 'clean fixture', $encoding)
    Assert-Audit $false

    New-FixtureRepo 'nul-secret'
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.py'), ([char]0 + $tokenFixture), $encoding)
    git add fixture.py
    Assert-Audit $false

    New-FixtureRepo 'json-secret'
    $jsonKey = 'api' + '_key'
    $jsonSecret = 'fixture-' + ('x' * 20)
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'fixture.json'),
        (@{$jsonKey=$jsonSecret} | ConvertTo-Json), $encoding)
    git add fixture.json
    Assert-Audit $false
    Write-Output "Audit regression tests passed: $testCount"
} finally {
    Pop-Location
    # Only this test's freshly-created temporary repositories are removed.
    Get-ChildItem -LiteralPath $testRoot -Recurse -Force -File | ForEach-Object { $_.IsReadOnly = $false }
    [System.IO.Directory]::Delete($testRoot, $true)
}
