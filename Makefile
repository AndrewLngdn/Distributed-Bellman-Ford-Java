.SUFFIXES: .java .class

.java.class:
	javac $<

CLASSES = Bfclient.class Serializer.class Node.class

all: $(CLASSES)

clean:
	rm *.class

