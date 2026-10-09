// Copyright © 2026 CCP ehf.

package _Self.buildTypes

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ReuseBuilds
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

/**
 * Publishes the final multi-arch tags (latest, x.y.z, ...) as manifest lists pointing at the per-architecture
 * images built by the given BuildAndPushImage build types. This is the build type that is triggered by VCS changes;
 * the per-architecture builds run as its snapshot dependencies.
 */
class BuildAndPushManifest(variant: String, archBuilds: List<BuildAndPushImage>, archs: List<String>) : BuildType({
    id("BuildAndPush_$variant")
    name = "Build and push carbon-linux-$variant (multi-arch)"

    enablePersonalBuilds = false

    params {
        param("variant", variant)
        param("image_name", "carbon-linux-$variant")
        param("archs", archs.joinToString(" "))
        param("vcs_ref", "%teamcity.build.vcs.branch.${DslContext.settingsRootId}%")
        param("teamcity.vcsTrigger.runBuildInNewEmptyBranch", "true")
    }

    vcs {
        root(DslContext.settingsRootId)
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    steps {
        script {
            name = "Create and push multi-arch manifests"
            scriptContent = """
                set -euo pipefail
                bash .teamcity/scripts/push-manifest.sh \
                    "%image_name%" \
                    "%vcs_ref%" \
                    "%teamcity.build.branch.is_default%" \
                    "%build.vcs.number%" \
                    "%ecr_registry%" \
                    "%archs%"
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:<default>
                +:v*
                +:pull/*
            """.trimIndent()
        }
    }

    features {
        pullRequests {
            vcsRootExtId = "${DslContext.settingsRootId.id}"
            provider = github {
                authType = token {
                    token = "%GITHUB_CARBON_PAT%"
                }
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
        sshAgent {
            teamcitySshKey = "ccpgames-carbon"
        }
        dockerSupport {
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_156"
            }
        }
    }

    dependencies {
        archBuilds.forEach { archBuild ->
            snapshot(archBuild) {
                reuseBuilds = ReuseBuilds.NO
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
        noLessThanVer("env.FENRIS_AGENT_VERSION", "1.0.0")
    }
})
