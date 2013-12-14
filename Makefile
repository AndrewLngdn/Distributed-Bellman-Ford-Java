.SUFFIXES: .java .class

.java.class:
	javac $<

CLASSES = Bfclient.class

all: $(CLASSES)

clean:
	rm *.class

