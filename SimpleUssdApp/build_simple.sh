#!/bin/bash

# Set Android SDK paths
export ANDROID_HOME=/Users/gideonglago/Library/Android/sdk
export PATH=$ANDROID_HOME/build-tools/35.0.0:$ANDROID_HOME/platform-tools:$PATH

echo "Building Simple USSD App..."

# Create build directories
mkdir -p build/gen
mkdir -p build/obj
mkdir -p build/apk

# Set paths
APP_DIR="app/src/main"
BUILD_DIR="build"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
APK_DIR="$BUILD_DIR/apk"

# Generate R.java
echo "Generating R.java..."
aapt package -f -m \
    -J $GEN_DIR \
    -M $APP_DIR/AndroidManifest.xml \
    -S $APP_DIR/res \
    -I $ANDROID_HOME/platforms/android-35/android.jar

# Compile Java files
echo "Compiling Java files..."
javac -d $OBJ_DIR \
    -classpath $ANDROID_HOME/platforms/android-35/android.jar \
    -sourcepath $APP_DIR/java:$GEN_DIR \
    $APP_DIR/java/com/yourpackage/simpleussd/*.java \
    $APP_DIR/java/com/yourpackage/simpleussd/ussd/*.java \
    $GEN_DIR/com/yourpackage/simpleussd/R.java

# Create DEX
echo "Creating DEX..."
$ANDROID_HOME/build-tools/35.0.0/d8 \
    --lib $ANDROID_HOME/platforms/android-35/android.jar \
    --output $APK_DIR \
    $OBJ_DIR/com/yourpackage/simpleussd/*.class \
    $OBJ_DIR/com/yourpackage/simpleussd/ussd/*.class

# Package APK
echo "Packaging APK..."
aapt package -f \
    -M $APP_DIR/AndroidManifest.xml \
    -S $APP_DIR/res \
    -I $ANDROID_HOME/platforms/android-35/android.jar \
    -F $APK_DIR/SimpleUssd-unsigned.apk \
    $APK_DIR

# Add DEX to APK
cd $APK_DIR
aapt add SimpleUssd-unsigned.apk classes.dex
cd ../../..

echo "APK created at: build/apk/SimpleUssd-unsigned.apk"
echo "Installing on device..."

# Install APK
adb install -r build/apk/SimpleUssd-unsigned.apk

echo "Done! App installed successfully."

