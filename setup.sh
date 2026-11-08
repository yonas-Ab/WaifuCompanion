clear
rm -rf /data/data/com.termux/files/home/.gradle/caches/9.9.0/transforms/
gradle clean assembleDebug --no-daemon -Dorg.gradle.workers.max=2
