import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.AfterClass;
//import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


public class SuperFilehandlerTest {

	static Proxy.FileHandler  handler1 = null;
	static Proxy.FileHandler  handler2 = null;
	static String serverDir = "tests";
	static String cacheDir1 = "cache1";//src/cache/../cache/../../tests/../../P2/cache1/./cache1";//"cache1";
	static String cacheDir2 = "cache2";
	
	@BeforeClass
	public static void setupServer(){
		// Setup server
		
				try {
					Server.main(new String[] {"1160", serverDir});
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				setUp1();
				setUp2();
	}

	public static void setUp1() {
		
		File f = new File(cacheDir1);
		File[] files = f.listFiles();
		for(int i = 0; i<files.length; i++){
			files[i].delete();
		}
		// TODO is return required and would autolab check it?
		int err = 0;
		err = Proxy.check_args(new String[]{"localhost","1160",cacheDir1,"5242880"});
		if (err < 0){
			System.err.println("Invalid arguments ");
			System.exit(1);
		}

		//Proxy.setProxyId();

		// if (System.getSecurityManager() == null)
		// System.setSecurityManager(new RMISecurityManager());
		Proxy.connect_to_server("localhost", "1160");
		
		//System.err.println("Hello World");
		handler1 = new Proxy.FileHandler();
		
    }

	public static void setUp2() {
		File f = new File(cacheDir2);
		File[] files = f.listFiles();
		for(int i = 0; i<files.length; i++){
			files[i].delete();
		}
		// TODO is return required and would autolab check it?
				int err = 0;
				err = Proxy.check_args(new String[]{"localhost","1160",cacheDir2,"2048576"});
				if (err < 0){
					System.err.println("Invalid arguments ");
					System.exit(1);
				}
					
				//Proxy.setProxyId();

				// if (System.getSecurityManager() == null)
				// System.setSecurityManager(new RMISecurityManager());
				Proxy.connect_to_server("localhost", "1160");
		
		//System.err.println("Hello World");
		handler2 = new Proxy.FileHandler();
		
    }
	@Test
	public void fileDoesNotExistRead() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");
				int fd = handler1.open("doesNotExist", FileHandling.OpenOption.READ);
				Assert.assertEquals(FileHandling.Errors.EBADF, fd);
				//handler1.close(fd);				
			}
		});
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void fileExistCreateNew() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");
				int fd = handler1.open("file1", FileHandling.OpenOption.CREATE_NEW);
				//handler1.close(fd);		
				Assert.assertEquals(FileHandling.Errors.EEXIST, fd);
			}
		});
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void fileOutsideServerDir() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");
				int fd = handler1.open("../file1", FileHandling.OpenOption.CREATE_NEW);
				//handler1.close(fd);		
				Assert.assertEquals(FileHandling.Errors.EPERM, fd);
			}
		});
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
	@Test
	public void concurrentFileAccess() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				handler1.close(fd);				
			}
		});
		
		Thread t2 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				handler1.close(fd);				
			}
		});
		t1.start();
		
		try {
			t1.join();
			t2.start();
			t2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void concurrentFileAccessMultipleProxy() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				handler1.close(fd);				
			}
		});
		
		Thread t2 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				handler1.close(fd);				
			}
		});
		
		Thread t3 = new Thread(new MyThread(handler2) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler2.open("file1", FileHandling.OpenOption.READ);
				handler2.close(fd);				
			}
		});
		
		Thread t4 = new Thread(new MyThread(handler2) {
			
			@Override
			public void run() {
				//System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler2.open("file1", FileHandling.OpenOption.READ);
				handler2.close(fd);				
			}
		});
		t1.start();
		t2.start();
		t3.start();
		t4.start();
		
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
	
	
	@Test
	public void fileDirNotExistOpen() {
		Thread ct = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				System.err.println("Hello from thread");

				byte[] readBuf = new byte[100];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);		
				//Assert.assertTrue(fd>0);
				handler1.close(fd);
				//Assert.assertTrue(readCount ==-9);
				fd = handler1.open("cache1/cache1/file1", FileHandling.OpenOption.READ);		
				//Assert.assertTrue(fd>0);
				handler1.close(fd);
				fd = handler1.open("../file1", FileHandling.OpenOption.READ);		
				//Assert.assertTrue(fd>0);
				handler1.close(fd);
			}
		});
		
		ct.start();
		try {
			ct.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void fileExistInServerRead() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file1").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					try {
						str = new String(readBuf,0, Long.valueOf(actualBytes).intValue(), "UTF-8");
						System.out.println("----------------");
						System.out.println(str);
						System.out.println("----------------");
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				
			}
		});
		
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void fileExistInServerReadHuge() {

		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				long time = System.nanoTime();
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					/*String str = null;
					try {
						str = new String(readBuf,0, Long.valueOf(actualBytes).intValue(), "UTF-8");
						System.out.println("----------------");
						System.out.println(str);
						System.out.println("----------------");
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}*/
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				long elapsed = (System.nanoTime()- time);
				System.err.print("Elapsed: " + elapsed/1000000 + " bytes read: " + sizeRead);
				System.err.flush();
			}
		});
		
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void fileExistInServerReadHugeConcurrent() {

		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				long time = System.nanoTime();
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				long elapsed = (System.nanoTime()- time);
				System.err.print("Elapsed: " + elapsed/1000000 + " bytes read: " + sizeRead);
				System.err.flush();
			}
		});
		Thread t2 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				long time = System.nanoTime();
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				long elapsed = (System.nanoTime()- time);
				System.err.print("Elapsed: " + elapsed/1000000 + " bytes read: " + sizeRead);
				System.err.flush();
			}
		});
		
		t1.start();
		t2.start();
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void fileExistInServerReadHugeConcurrentMultipleProxy() {
		long time = System.nanoTime();
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {

				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				
			}
		});
		Thread t2 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				long time = System.nanoTime();
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});

		Thread t3 = new Thread(new MyThread(handler2) {
			
			@Override
			public void run() {
				byte[] readBuf = new byte[1024*1024];
				int fd = handler2.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler2.read(fd, readBuf);
					sizeRead+=actualBytes;
				}
				handler2.close(fd);
			}
		});
		Thread t4 = new Thread(new MyThread(handler2) {
			
			@Override
			public void run() {
				byte[] readBuf = new byte[1024*1024];
				int fd = handler2.open("hugefile", FileHandling.OpenOption.READ);
				if (fd < 0)
					return;
				long size = new File(serverDir+File.separator+"hugefile").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler2.read(fd, readBuf);
					sizeRead+=actualBytes;
				}
				handler2.close(fd);
			}
		});
		
		t1.start();
		t2.start();
		t3.start();
		t4.start();
		try {
			t1.join();
			t2.join();
			t3.join();
			t4.join();
			long elapsed = (System.nanoTime()- time);
			System.err.print("Elapsed: " + elapsed/1000000 );
			System.err.flush();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void fileDoesNotExistInServerCreate() {

		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				byte[] writeBuf = "this is a gift".getBytes();
				int fd = handler1.open("clientgift.txt", FileHandling.OpenOption.CREATE);
				long actualBytes = handler1.write(fd, writeBuf);
				handler1.close(fd);
			}
		});
		t1.start();
		
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	//TODO has race conditions. see it later
	public void fileExistInServerConcurrentWrite() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				byte[] writeBuf = "I am client 1. bwhahahahahahahhahah".getBytes();
				int fd = handler1.open("clientgift", FileHandling.OpenOption.WRITE);
				long actualBytes = handler1.write(fd, writeBuf);
				handler1.close(fd);				
			}
		});
		
		Thread t2 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				byte[] writeBuf = "I am client 2. lolololollllllooooooolll".getBytes();
				int fd = handler1.open("clientgift", FileHandling.OpenOption.WRITE);
				long actualBytes = handler1.write(fd, writeBuf);
				handler1.close(fd);				
			}
		});
		
		t1.start();
		t2.start();
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void fileExistInServerHugeWrite() {
	
		new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				System.err.println("Hello from thread");

				byte[] writeBuf = "ttestonasas".getBytes();
				int fd = handler1.open("clientgift.txt", FileHandling.OpenOption.WRITE);
				
				long total =0;
				long actualBytes;
				for(int i = 0;i <10000;i++){
					actualBytes = handler1.write(fd, writeBuf);
					total+= actualBytes;
				}
				
				handler1.close(fd);
				
				
				System.out.println("Got message: "+  total);
				
			}
		}).start();
	}
	
	@Test
	public void fileUpdateWriteAndRead() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				byte[] writeBuf = "File update from thread t1: this is a gift.- well that changed alotBazingasszss".getBytes();
				int fd = handler1.open("clientgift", FileHandling.OpenOption.WRITE);
				
				long actualBytes = handler1.write(fd, writeBuf);
				handler1.close(fd);
				
				fd = handler1.open("clientgift", FileHandling.OpenOption.READ);
				byte[] readBuf = new byte[1024*1024];
				long size = new File(serverDir+File.separator+"clientgift").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
					String str = null;
					try {
						str = new String(readBuf,0, Long.valueOf(actualBytes).intValue(), "UTF-8");
						System.out.println(str);
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
				handler1.close(fd);
			}
		});
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void fileUpdateWriteAndReadConcurrent() {
		
		Thread t1 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				byte[] writeBuf = "this is a gift from client 1".getBytes();
				int fd = handler1.open("clientgift", FileHandling.OpenOption.WRITE);
				
				try {
					TimeUnit.SECONDS.sleep(3);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				long actualBytes = handler1.write(fd, writeBuf);
				handler1.close(fd);
				
				fd = handler1.open("clientgift", FileHandling.OpenOption.READ);
				byte[] readBuf = new byte[1024*1024];
				long size = new File(serverDir+File.separator+"clientgift").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
					String str = null;
					try {
						str = new String(readBuf,0, Long.valueOf(actualBytes).intValue(), "UTF-8");
						System.out.println(str);
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
				handler1.close(fd);
			}
		});
		
		Thread t2 = new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				int fd = handler1.open("clientgift", FileHandling.OpenOption.READ);
				
				byte[] readBuf = new byte[1024*1024];
				long size = new File(serverDir+File.separator+"clientgift").length();
				long sizeRead = 0L;
				long actualBytes;
				while(sizeRead < size){
					actualBytes = handler1.read(fd, readBuf);
					sizeRead+=actualBytes;
					String str = null;
					try {
						str = new String(readBuf,0, Long.valueOf(actualBytes).intValue(), "UTF-8");
						System.out.println(str);
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
				handler1.close(fd);
			}
		});
				
		t1.start();
		t2.start();
		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	@Test
	public void fileDoubleRead() {
		

		new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				//byte[] writeBuf = "this is a gift- well that changed alotBazingasszss".getBytes();
				byte[] readBuf = new byte[100];
				int fd = handler1.open("filewrite.txt", FileHandling.OpenOption.READ);
				
				long actualBytes = handler1.read(fd, readBuf);
				
//				Thread t = new Thread(new ServerThread(serverDir) {
//					
//					@Override
//					public void run() {
//						try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(serverDir+File.separatorChar+"filewrite.txt", true)))) {
//						    out.println("appendos");
//						}catch (IOException e) {
//						    //exception handling left as an exercise for the reader
//						}
//						
//					}
//				});
//				t.start();
//				try {
//					t.join();
//				} catch (InterruptedException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
				handler1.close(fd);
				fd = handler1.open("filewrite.txt", FileHandling.OpenOption.READ);
				
				readBuf = new byte[100];
				actualBytes = handler1.read(fd, readBuf);
				handler1.close(fd);

				String str = null;
				try {
					str = new String(readBuf,0, Long.valueOf(actualBytes).intValue(), "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}).start();
	}
	
	@Test
	public void fileDoubleReadBig() {
	
		new Thread(new MyThread(handler1) {
			
			@Override
			public void run() {
				System.err.println("Hello from thread");

				//byte[] writeBuf = "this is a gift- well that changed alotBazingasszss".getBytes();
				byte[] readBuf = new byte[100];
				int fd = handler1.open("out.txt", FileHandling.OpenOption.READ);
				handler1.close(fd);
				fd = handler1.open("out.txt", FileHandling.OpenOption.READ);
				handler1.read(fd, readBuf);
				handler1.close(fd);
				handler1.clientdone();
				}}).start();			
	}
	
	@Test
	public void basicLRU(){
		
		Thread t1 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				for(int i =1 ; i<5;i++){
					String fileName = "file"+i;
					int fd = handler1.open(fileName, FileHandling.OpenOption.READ);
					long size = new File(serverDir+File.separator+fileName).length();
					long sizeRead = 0L;
					while(sizeRead < size){
						long actualBytes = handler1.read(fd, readBuf);
						String str = null;
						sizeRead+=actualBytes;
					}
					handler1.close(fd);
				}
				
				int fd = handler1.open("file5", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file5").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				
				fd = handler1.open("file2", FileHandling.OpenOption.READ);
				size = new File(serverDir+File.separator+"file2").length();
				sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				
				fd = handler1.open("file3", FileHandling.OpenOption.READ);
				size = new File(serverDir+File.separator+"file3").length();
				sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				
				fd = handler1.open("file1", FileHandling.OpenOption.READ);
				size = new File(serverDir+File.separator+"file1").length();
				sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
				
				
			}
		});
		t1.start();
		try {
			t1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
@Test
public void advancedLRU(){
		
		Thread t1 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file1").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
		}
			});
		
		Thread t2 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file1").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		
		Thread t3 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file1").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t4 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file1").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t5 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file1", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file1").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t6 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file2", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file2").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t7 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file3", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file3").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t8 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file4", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file4").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t9 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file5", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file5").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		
		Thread t10 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file6", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file6").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t11 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file7", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file7").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		Thread t12 = new Thread(new MyThread(handler1){
			@Override
			public void run(){
				byte[] readBuf = new byte[1024*1024];
				int fd = handler1.open("file8", FileHandling.OpenOption.READ);
				long size = new File(serverDir+File.separator+"file8").length();
				long sizeRead = 0L;
				while(sizeRead < size){
					long actualBytes = handler1.read(fd, readBuf);
					String str = null;
					sizeRead+=actualBytes;
				}
				handler1.close(fd);
			}
		});
		
		t1.start();
		t2.start();
		t3.start();
		t4.start();
		t5.start();
		try {
			t1.join();
			t2.suspend();
			t3.suspend();
			t4.suspend();
			t5.suspend();
			t6.start();
			t7.start();
			t8.start();
			t9.start();
			t10.start();
			t11.start();
			t12.start();
			
			t2.resume();
			t3.resume();
			t4.resume();
			t5.resume();
			t2.join();
			t3.join();
			t4.join();
			t5.join();
			t6.join();
			t7.join();
			t8.join();
			t9.join();
			t10.join();
			t11.join();
			t12.join();
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/*public void filePrivateCopy(){
		String fileName = "src";
		String destFile = "copy";
		File src = new File(CacheMgr.createPathName(cacheDir1, fileName));
		System.err.println("Source exists " + src.exists());
		File dest = new File(CacheMgr.createPathName(cacheDir1, destFile));
		System.err.println("Dest exists " + dest.exists());
		try {
			dest.createNewFile();
			CacheMgr.createPrivateCopy(src, dest);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/
	 @AfterClass
	    public static void tearDown() {
	    	System.out.println("tearing down");
	    }
	
	private abstract class MyThread implements Runnable{

		private final FileHandling filehandler;
		
		public MyThread(FileHandling handler){
			this.filehandler = handler;
		}
		
	}
	

}
