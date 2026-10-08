package _Self

import _Self.buildTypes.BuildAndPushImage
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Project

object Project : Project({

    description = "Build / Publish pipeline for https://github.com/carbonengine/linux-images"

    params {
        param("ecr_registry", "906334554726.dkr.ecr.eu-west-1.amazonaws.com")
    }

    val variants = listOf("gcc", "clang")

    variants.forEach { variant ->
        buildType(BuildAndPushImage(variant))
    }
})
