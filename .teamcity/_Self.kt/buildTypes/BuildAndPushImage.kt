// Copyright © 2026 CCP ehf.

package _Self.buildTypes

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

class BuildAndPushImage(variant: String) : BuildType({
    id("BuildAndPush_$variant")
    name = "Build and push carbon-linux-$variant"

    enablePersonalBuilds = false

    params {
        param("variant", variant)
        param("image_name", "carbon-linux-$variant")
        /* build context relative to the repo root, and the Dockerfile name inside it (these images use the podman-style name) */
        param("context_dir", "build/$variant")
        param("dockerfile", "Containerfile")
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
            name = "Build and push to ECR"
            scriptContent = """
                set -euo pipefail
                bash .teamcity/scripts/build-and-push.sh \
                    "%image_name%" \
                    "%vcs_ref%" \
                    "%teamcity.build.branch.is_default%" \
                    "%build.vcs.number%" \
                    "%ecr_registry%" \
                    "%context_dir%" \
                    "%dockerfile%"
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

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
