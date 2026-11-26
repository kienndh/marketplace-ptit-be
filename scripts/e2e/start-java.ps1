# Khởi động Java (profile e2e) cho kiểm thử FE. Cổng mặc định 8081 (8080 trên máy này bị dịch vụ khác chiếm).
param([string]$FirebaseProjectId = "chopiters-5739b", [int]$Port = 8081, [string]$Log = "$env:TEMP\java-e2e.log")
$pw = (docker inspect sm_postgres --format '{{range .Config.Env}}{{println .}}{{end}}' | Where-Object { $_ -like 'POSTGRES_PASSWORD=*' }) -replace '^POSTGRES_PASSWORD=',''
$env:E2E_DB_PASSWORD = $pw
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Write-Error ("Chưa đặt JAVA_HOME (hoặc đường dẫn không có bin\java.exe). Đặt trước khi chạy, ví dụ:`n" +
                 '  $env:JAVA_HOME = "C:\Users\<ten>\.jdks\openjdk-21"' + "`n" +
                 "(mỗi máy tự đặt đường dẫn JDK của mình; khuyến nghị JDK 21).")
    exit 1
}
$env:JDK_JAVAC_OPTIONS = "-proc:full"
$env:SPRING_PROFILES_ACTIVE = "e2e"; $env:FIREBASE_DEV_PROJECT_ID = $FirebaseProjectId
$env:REDIS_HOST = "localhost"; $env:SERVER_PORT = "$Port"
$env:MARKETPLACE_E2E_MEDIA_BASE_URL = "http://localhost:$Port/e2e-media/"
$env:MARKETPLACE_WORKER_ORDER_AUCTION_ENABLED = "true"; $env:MARKETPLACE_WORKER_POLL_INTERVAL_MS = "5000"
$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
Start-Process -FilePath ".\mvnw.cmd" -ArgumentList '"-Dmaven.compiler.fork=true"','"-Dlombok.version=1.18.46"','spring-boot:run' -RedirectStandardOutput $Log -RedirectStandardError "$Log.err" -WindowStyle Hidden
