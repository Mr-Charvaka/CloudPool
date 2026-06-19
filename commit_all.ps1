$statusOutput = git status --porcelain

foreach ($line in $statusOutput) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    
    # Git porcelain status has 2 chars for status, a space, then the file path.
    $status = $line.Substring(0, 2)
    $file = $line.Substring(3)
    
    # Extract filename for commit message
    $filename = Split-Path $file -Leaf
    
    $action = "Update"
    if ($status -match "A" -or $status -match "\?\?") { $action = "Add" }
    elseif ($status -match "D") { $action = "Remove" }
    
    # Trim quotes if any
    $file = $file -replace '^"|"$',''
    
    # Add the specific file
    git add $file
    
    # Commit with message
    $commitMsg = "$action $filename"
    git commit -m "$commitMsg"
}

# Push to origin main
git push origin main
