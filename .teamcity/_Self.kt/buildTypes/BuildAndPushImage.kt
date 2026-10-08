// Copyright © 2026 CCP ehf.

package _Self.buildTypes

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/**
 * Builds one architecture of an image on a native agent and pushes it under the unique "<tag>-<arch>" tag.
 * BuildAndPushManifest then stitches the per-architecture images into the final multi-arch tags.
 *
 * @param arch value of teamcity.agent.jvm.os.arch of the agent to build on ("amd64" or "aarch64"), also used as the tag suffix
 */
class BuildAndPushImage(variant: String, arch: String) : BuildType({
    id("BuildAndPush_${variant}_$arch")
    name = "Build and push carbon-linux-$variant ($arch)"

    enablePersonalBuilds = false

    params {
        param("variant", variant)
        param("arch", arch)
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
                    "%dockerfile%" \
                    "%arch%"
            """.trimIndent()
        }
    }

    features {
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
        noLessThanVer("env.FENRIS_AGENT_VERSION", "1.0.0")
        equals("teamcity.agent.jvm.os.arch", arch)
    }
})
