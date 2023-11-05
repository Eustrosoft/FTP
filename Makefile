
usage:
	@echo "Usage: make (all|clean|package)"
all: clean package
	@echo "Completed"
clean:
	mvn clean
package:
	mvn package
