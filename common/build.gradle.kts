plugins {
    `java-library`
}

applyPlatformAndCoreConfiguration(javaRelease = 21)
applyCommonArtifactoryConfig()

dependencies {
    "api"(project(":nuvotifier-api"))
    "implementation"("io.netty:netty-handler:${Versions.NETTYIO}")
    "implementation"("io.netty:netty-transport-native-epoll:${Versions.NETTYIO}:linux-x86_64")
    "implementation"("com.google.code.gson:gson:${Versions.GSON}")
    "testImplementation"("org.json:json:20240303")
    "testImplementation"("com.google.guava:guava:33.3.1-jre")
}