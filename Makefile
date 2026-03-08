# Author: 7ANG0N1N3 — https://github.com/7ang0n1n3/lg-signage
IMAGE       := mingc/android-build-box
PROJECT     := $(shell pwd)
APK_DEBUG   := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE := app/build/outputs/apk/release/app-release-unsigned.apk

.PHONY: build release install install-release clean

build:
	docker run --rm -v "$(PROJECT):/app" $(IMAGE) bash -c \
		"cd /app && gradle wrapper --gradle-version=7.5 2>/dev/null; chmod +x gradlew && ./gradlew assembleDebug"

release:
	docker run --rm -v "$(PROJECT):/app" $(IMAGE) bash -c \
		"cd /app && gradle wrapper --gradle-version=7.5 2>/dev/null; chmod +x gradlew && ./gradlew assembleRelease"

install: build
	adb install -r $(APK_DEBUG)

install-release: release
	adb install -r $(APK_RELEASE)

clean:
	docker run --rm -v "$(PROJECT):/app" $(IMAGE) bash -c \
		"cd /app && ./gradlew clean"
