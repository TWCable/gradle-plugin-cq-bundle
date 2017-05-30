# Purpose #

This contains a number of plugins for making it easier to work with Gradle and Adobe CQ/AEM.

[ ![Download](https://api.bintray.com/packages/twcable/aem/gradle-plugin-cq-bundle/images/download.svg) ](https://bintray.com/twcable/aem/gradle-plugin-cq-bundle/_latestVersion) [![Stories in Ready](https://badge.waffle.io/TWCable/gradle-plugin-cq-bundle.png?label=ready&title=Ready)](https://waffle.io/TWCable/gradle-plugin-cq-bundle) 

# CQ Bundle Plugin #

See [the CQ Bundle Plugin documentation](docs/CqBundlePlugin.adoc)


# Installation #

```
buildscript {
    repositories {
        maven {
            url "http://dl.bintray.com/twcable/aem"
        }
    }

    dependencies {
        classpath "com.twcable.gradle:gradle-plugin-cq-bundle:<version>"
    }
}
```

Built against *Gradle 3.5* and *JDK 1.8*

# API #

https://twcable.github.io/gradle-plugin-cq-bundle/groovydoc/

# LICENSE

Copyright 2014-2017 Time Warner Cable, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
the specific language governing permissions and limitations under the License.
