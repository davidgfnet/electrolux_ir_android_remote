# --- Config (override on the command line or via env) ---
BUILD_TOOLS_VERSION  ?= 34.0.0
PLATFORM_VERSION     ?= android-34
JAVAC_FLAGS          ?= --release 11
KEYSTORE             ?= debug.keystore

ifeq ($(strip $(ANDROID_SDK)),)
  $(error ANDROID_SDK is not set — e.g. `make ANDROID_SDK=$$HOME/android-sdk`)
endif

BT       := $(ANDROID_SDK)/build-tools/$(BUILD_TOOLS_VERSION)
PLATFORM := $(ANDROID_SDK)/platforms/$(PLATFORM_VERSION)/android.jar
AAPT2    := $(BT)/aapt2
D8       := $(BT)/d8
ZIPALIGN := $(BT)/zipalign
APKSIGNER:= $(BT)/apksigner

PKG_PATH := net/davidgf/elremote
SRC      := $(wildcard src/*.java)
BUILD    := build
APK      := acremote.apk

all: $(APK)

$(BUILD):
	mkdir -p $(BUILD)
clean:
	rm -rf $(BUILD) $(APK)

# Manifest -> base apk (no resources used)
$(BUILD)/base.apk: AndroidManifest.xml | $(BUILD)
	$(AAPT2) link -o $@ -I $(PLATFORM) --manifest AndroidManifest.xml

# Java -> classes -> classes.dex
$(BUILD)/classes.dex: $(SRC) | $(BUILD)
	mkdir -p $(BUILD)/classes
	javac $(JAVAC_FLAGS) -classpath $(PLATFORM) -d $(BUILD)/classes $(SRC)
	$(D8) --lib $(PLATFORM) --output $(BUILD) $(BUILD)/classes/$(PKG_PATH)/*.class

# Assemble unsigned apk
$(BUILD)/unsigned.apk: $(BUILD)/base.apk $(BUILD)/classes.dex
	cp $(BUILD)/base.apk $@
	cd $(BUILD) && zip -j unsigned.apk classes.dex

# Debug keystore (created once)
debug.keystore:
	keytool -genkeypair -keystore $@ -alias androiddebugkey \
	  -storepass android -keypass android -keyalg RSA -keysize 2048 \
	  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

# Align + sign
$(APK): $(BUILD)/unsigned.apk $(KEYSTORE)
	$(ZIPALIGN) -f 4 $(BUILD)/unsigned.apk $(BUILD)/aligned.apk
	$(APKSIGNER) sign --ks $(KEYSTORE) --ks-pass pass:android --out $@ $(BUILD)/aligned.apk

install: $(APK)
	$(ANDROID_SDK)/platform-tools/adb install -r $(APK)

.PHONY: all install clean
