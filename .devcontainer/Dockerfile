# Android Development Container for MedicineShield
FROM ubuntu:22.04

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Install basic dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    unzip \
    wget \
    openjdk-17-jdk \
    build-essential \
    libssl-dev \
    libffi-dev \
    python3 \
    python3-pip \
    sudo \
    && rm -rf /var/lib/apt/lists/*

# Set Java environment
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create vscode user
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Switch to vscode user
USER $USERNAME
WORKDIR /home/$USERNAME

# Android SDK environment variables
ENV ANDROID_HOME=/home/$USERNAME/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

# Download and install Android SDK Command Line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip commandlinetools-linux-11076708_latest.zip && \
    rm commandlinetools-linux-11076708_latest.zip && \
    mv cmdline-tools latest

# Accept licenses (will be done again in postCreateCommand)
RUN yes | sdkmanager --licenses || true

# Install Android SDK packages
# Based on MedicineShield requirements: compileSdk 34, minSdk 24, targetSdk 34
RUN sdkmanager --install \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "extras;android;m2repository" \
    "extras;google;m2repository"

# Install Kotlin compiler (optional, but useful for standalone Kotlin development)
RUN cd /tmp && \
    wget -q https://github.com/JetBrains/kotlin/releases/download/v1.9.22/kotlin-compiler-1.9.22.zip && \
    unzip kotlin-compiler-1.9.22.zip && \
    sudo mv kotlinc /opt/kotlinc && \
    rm kotlin-compiler-1.9.22.zip

ENV PATH="/opt/kotlinc/bin:${PATH}"

# Set up Gradle wrapper permissions (will be fixed when project is mounted)
WORKDIR /workspace

# Default command
CMD ["/bin/bash"]
