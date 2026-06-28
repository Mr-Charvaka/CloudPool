$files = git status -s | ForEach-Object { $_.Substring(3) }
foreach ($f in $files) {
    if ([string]::IsNullOrWhiteSpace($f)) { continue }
    $fname = Split-Path $f -Leaf
    $dname = Split-Path $f -Parent
    $msg = "Update $fname in $dname"
    
    if ($fname.EndsWith(".yml") -or $fname.EndsWith(".properties") -or $fname.EndsWith(".rs") -or $fname.EndsWith(".xml") -or $fname.EndsWith(".yaml") -or $fname -eq ".env.example") {
        $msg = "Configure $fname for $dname"
    } elseif ($fname.EndsWith("Test.java")) {
        $msg = "Add or update test $fname"
    } elseif ($fname.EndsWith(".java")) {
        $msg = "Implement feature or fix in $fname"
    } elseif ($fname.EndsWith(".sql")) {
        $msg = "Update database schema/migrations $fname"
    } elseif ($fname.EndsWith(".ps1")) {
        $msg = "Update script $fname"
    } elseif ($fname.EndsWith(".err") -or $fname.EndsWith(".txt") -or $fname -eq "Hash.java" -or $fname -eq "MockMaker") {
        $msg = "Update build output/temp file $fname"
    }
    
    Write-Host "Committing $f -> $msg"
    git add "$f"
    git commit -m "$msg"
}
git push
