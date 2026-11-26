# Dừng phiên Java (e2e) đang nghe cổng chỉ định (mặc định 8081) cùng các tiến trình mvnw/maven cha.
# Chỉ dừng tiến trình java.exe; nếu cổng bị tiến trình khác chiếm (vd. 8080) thì từ chối, không đụng tới.
param([int]$Port = 8081)
$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $conn) { Write-Host "Không có tiến trình nào đang nghe cổng $Port."; exit 0 }
$procId = $conn.OwningProcess
$p = Get-CimInstance Win32_Process -Filter "ProcessId = $procId"
if ($p.Name -ne 'java.exe') {
    Write-Error "Cổng $Port đang do '$($p.Name)' (PID $procId) giữ, không phải java.exe của dự án: KHÔNG dừng."
    exit 1
}
$targets = @($p)
$cur = $p
while ($cur.ParentProcessId) {
    $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $($cur.ParentProcessId)"
    if (-not $parent -or $parent.CommandLine -notmatch 'mvnw|maven|spring-boot:run|plexus-classworlds') { break }
    $targets += $parent
    $cur = $parent
}
foreach ($t in $targets) {
    $cmd = if ($t.CommandLine) { $t.CommandLine.Substring(0, [Math]::Min(90, $t.CommandLine.Length)) } else { '' }
    Stop-Process -Id $t.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "Đã dừng PID $($t.ProcessId) ($($t.Name)): $cmd..."
}
