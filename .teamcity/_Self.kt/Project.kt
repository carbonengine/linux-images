package _Self

import _Self.buildTypes.BuildAndPushImage
import _Self.buildTypes.BuildAndPushManifest
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Project

object Project : Project({

    description = "Build / Publish pipeline for https://github.com/carbonengine/linux-images"

    params {
        param("ecr_registry", "906334554726.dkr.ecr.eu-west-1.amazonaws.com")
    }

    val variants = listOf("gcc", "clang")
    val archs = listOf("amd64", "aarch64")

    val buildsByArch = archs.map { arch ->
        arch to variants.map { variant -> BuildAndPushImage(variant, arch) }
    }

    buildsByArch.forEach { (arch, builds) ->
        subProject(Project({
            id("LinuxImages_${arch.uppercase()}_ImageBuilds")
            name = "Linux ${arch.uppercase()} image builds"
            description = "Per-architecture $arch image builds that feed the multi-arch images"
            builds.forEach { buildType(it) }
        }))
    }

    variants.forEachIndexed { index, variant ->
        buildType(BuildAndPushManifest(variant, buildsByArch.map { (_, builds) -> builds[index] }, archs))
    }
})
