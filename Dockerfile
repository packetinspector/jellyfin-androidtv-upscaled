# Use amd64 platform for Android build tools compatibility
FROM --platform=linux/amd64 eclipse-temurin:21-jdk

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/35.0.0"

# Install required packages
RUN apt-get update && apt-get install -y \
    unzip \
    wget \
    git \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android command line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept licenses
RUN yes | sdkmanager --licenses

# Install Android SDK components
RUN sdkmanager \
    "platforms;android-36" \
    "build-tools;35.0.0" \
    "platform-tools"

WORKDIR /app

# Set Gradle user home to a directory inside the project for caching
ENV GRADLE_USER_HOME=/app/.gradle-home
