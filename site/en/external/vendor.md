Project: /_project.yaml
Book: /_book.yaml
keywords: product:Bazel,vendor,offline,Bzlmod

# Vendor Mode

{% include "_buttons.html" %}

In certain development environments, such as air-gapped or restricted network
environments, accessing external repositories for dependency management may not
be feasible. In such cases, vendor mode proves invaluable for facilitating
development in isolated environments.

Vendor mode, currently in *beta version*, lets you download all your project's
external dependencies (libraries, packages) and store them directly within your
source control system.

## How it works {:#vendor-logic}

Vendor mode is activated using the flag:
```
--vendor_dir=/path/to/vendor/directory
```
Using the vendor command, dependencies are fetched and stored under the
specified path or under {workspace}/{path} if relevant. If the folder doesn't
exist, the vendor command will create it.

Once vendoring is complete, all dependencies can be found under that folder, 
which can be used in the project's build process facilitating various
advantages:
*   Ensuring consistent builds across your team.
*   Exercising control over dependency updates.
*   Managing specific dependencies independently (e.g., ongoing maintenance).

#### Exclusions from Vendoring
Certain repositories are excluded from vendoring:
*   Local repositories.
*   Repositories marked as configure.
*   Repositories specified in a .vendorignore file within the vendor directory.


## Vendor Contents {:#vendor-content}
*   Vendored repos: A folder for each vendored repo
*   Marker files: A marker file for each repo containing its vendored state to
be able to maintain its up-to-dateness
*   .vendorignore File: A File contains comma seperated repo names to be ignored
from vendoring


## .vendorignore File {:#vendor-ignore}

This file, generated under your vendor directory, lists repository names to be 
excluded from vendoring. Reasons for exclusion include:
*   Repositories always to be fetched.
*   Repositories under vendor that shouldn't be updated.
*   Repositories to be maintained or updated independently.

After including any repo name in this file, vendoring will never attempt to 
download/update these repos

## How to use {:#how-to-vendor}

### Vendoring
*   To download/update all dependencies into a vendor folder:
```
bazel vendor --vendor_dir={relative_path}/{absolute_path}
```

*   To vendor a particular repository/s
```
bazel vendor --vendor_dir={path}
--repo={@apparent_repo_name} --repo={@@canonical_repo_name}
```

*   To vendor a particular target/s
```
bazel vendor {some_target} {some_target} --vendor_dir={path}
```
Vendor target brings the repos your target transitively depends on, but not 
necessarily everything needed to build it as some toolchain resolution may be 
fetched during build

### Building with vendored repos

*   To build using the vendored repositories
```
bazel build {some_target} --vendor_dir={path}
```
This prioritizes repositories under the vendor directory; if they're outdated 
or missing, the build falls back to the standard flow of checking external cache
and fetching the repo if necessary

*   To build “offline” using vendored repos
```
bazel build {some_target} --vendor_dir={path} --nofetch
```

This will build with what exists under the vendor directory even if it is 
out-of-date.
It would only fail if the repository doesn’t exist under vendor

Building offline in vendor mode is reliable only when all dependencies are vendored; otherwise, missing repos or toolchains could lead to build failures.


### Example Use Case

For security reasons a fully offline builds is needed with checked-in 
dependencies to run release builds on secure machines (which don't have 
network access).

This Can be achieved by:
1. vendoring --all before running CI or release
2. Building with --nofetch to prevent any network access and guarantee offline
builds


### Feedback and Contributions

Feedback is vital for enhancing the vendor mode experience. Here's how you can
participate:

*   User Experience: Share your thoughts on vendor mode's usability and suggest 
improvements.
*   Bug Reports: Report any encountered bugs or unexpected behavior, providing
detailed steps to reproduce the issue. 
*   Use cases: Share your ideas or use cases to improve workflow efficiency.

