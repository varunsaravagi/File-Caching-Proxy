all: Proxy.class Server.class RmiInterface.class FileCache.class CacheMgr.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
