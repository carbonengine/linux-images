linux-images
---

Containerfiles for building CARBON on Linux.

### Building the container

```shell
# for the GCC version
podman build -t carbon-linux-gcc-buildenv:example-build build/gcc/
# for the clang version
podman build -t carbon-linux-clang-buildenv:example-build build/clang/
```

### Using the container

The containers do not contain any source code, they only provide a build environment for CARBON. Therefore, to use them
for building a CARBON component, code needs to be mounted or copied into the container. 

Here is an example building https://github.com/carbonengine/core using the GCC container built above. It assumes a 
CMake preset called "carbon-linux-gcc-buildenv", which at minimum looks like this:
```json
{
  "configurePresets": [
    {
      "name": "carbon-linux-gcc-buildenv",
      "inherits": "common"
    }
  ]
}
```

Once that preset exists, one possible way to run a build inside the container is the following:

```shell
podman run --rm -v /host/path/to/carbonengine/core/:/tmp/core carbon-linux-gcc-buildenv:example-build /usr/bin/cmake --preset carbon-linux-gcc-buildenv -S /tmp/core -B /tmp/core/.cmake-build-carbon-linux-gcc-buildenv-example
```
