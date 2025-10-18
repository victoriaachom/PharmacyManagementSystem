#!/bin/bash

package_name="PharmacyManagementSystem"

./build.sh
java -jar "${package_name}.jar"
./clean.sh
