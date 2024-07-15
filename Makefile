SRC_DIR = src/java
BUILD_DIR = build

JAR_PATH = $(BUILD_DIR)/btsm.jar
MANIFEST = manifest

JAVAC = javac
JAVAC_FLAGS = -d $(BUILD_DIR) -sourcepath $(SRC_DIR)

JAR = jar
JAR_FLAGS = -cvfm $(JAR_PATH) $(MANIFEST) -C $(BUILD_DIR) .

SOURCES = $(shell find $(SRC_DIR) -name "*.java")
CLASSES = $(SOURCES:$(SRC_DIR)/%.java=$(BUILD_DIR)/%.class)

# default target
all: jar

$(BUILD_DIR):
	mkdir $(BUILD_DIR)

# javac
$(BUILD_DIR)/%.class: $(SRC_DIR)/%.java
	$(JAVAC) $(JAVAC_FLAGS) $<

# copy README.md
$(BUILD_DIR)/README.md: README.md
	cp README.md $(BUILD_DIR)/

# copy LICENSE
$(BUILD_DIR)/LICENSE: LICENSE
	cp LICENSE $(BUILD_DIR)/

# jar
$(JAR_PATH): $(BUILD_DIR) $(CLASSES) $(BUILD_DIR)/README.md $(BUILD_DIR)/LICENSE
	rm -f $(JAR_PATH)
	$(JAR) $(JAR_FLAGS)

jar: $(JAR_PATH)

# clean
.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
