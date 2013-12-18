.SUFFIXES: .java .class

.java.class:
	javac $<

CLASSES = Bfclient.class Serializer.class

all: $(CLASSES)

clean:
	rm *.class

