#!/bin/bash

package_name="PharmacyManagementSystem"

javac -Xlint:deprecation -Xlint:unchecked -Xlint:serial -d build -g ${package_name}/*.java
jar cfe "${package_name}.jar" "${package_name}.Main" -C build/ ${package_name}
