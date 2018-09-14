#!/bin/bash

kotlinModuleDirs=`find . -wholename **/test/kotlin -print`
for t in ${kotlinModuleDirs}; do
    s=`expr "$t" : '\(.*src\)'`
    t=${s::${#s}-4}
    u=${t:2}
    moduleName=`echo $u | sed 's/\//:/g' | sed 's/docs:source://g' | sed 's/^testing://g'`
    echo Kotlin module: ${moduleName}
    if [ ${moduleName} = "node" ]; then
        echo Skipping Kotlin module ${moduleName}
        continue
    fi
    ./gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} ${moduleName}:clean ${moduleName}:test --build-cache ${PERFORM_GRADLE_SCAN}
done

javaModuleDirs=`find . -wholename **/test/java -print`
for t in ${javaModuleDirs}; do
    s=`expr "$t" : '\(.*src\)'`
    if [ ${#s} -le 4 ]; then
        echo Skipping module directory ${t}
        continue
    fi
    t=${s::${#s}-4}
    u=${t:2}
    moduleName=`echo $u | sed 's/\//:/g' | sed 's/docs:source://g' | sed 's/^testing://g'`
    echo Java module: ${moduleName}
    if [ ${moduleName} = "node" ]; then
        echo Skipping Java module ${moduleName}
        continue
    fi
    ./gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} ${moduleName}:clean ${moduleName}:test --build-cache ${PERFORM_GRADLE_SCAN}
done

nodeModuleDirs=`find node/src/*/*/net/corda/node -type d -regex ".*/test/.*"`
for t in ${nodeModuleDirs}; do
    if [[ $t = *"resources"* ]]; then
        echo Skipping module directory ${t}
        continue
    fi
    s=`echo $t | sed -E 's/.*(java|kotlin)//'`
    testPackageName=`echo ${s:1} | sed 's/\//./g'`
    echo Node module test package: ${testPackageName}
    ./gradlew --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} node:clean node:test --tests ${testPackageName} --build-cache ${PERFORM_GRADLE_SCAN}
done
