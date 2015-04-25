
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.*;
import java.util.concurrent.ConcurrentHashMap;



/**
 * @author vsaravag
 * Server class. Handles all the operations required on the server side
 */
public class Server extends UnicastRemoteObject implements RmiInterface {

	private static final long serialVersionUID = 1L;
	// Server directory
	private static String dir = null;
	// Maximum block(chunk) size
	private static final int MAX_BLOCK_SIZE = 1024 * 1024;
	
	/*
	 * Map to keep track of what files are currently being sent/updated in the server.
	 * <K, V>	: 	V > 0 -> file is being sent to the client
	 * 			:	V < 0 -> file is being updated
	 */ 
	private static ConcurrentHashMap<String, Integer> sessionFileLock = new ConcurrentHashMap<String, Integer>();
	// object to handle locking
	private Object lock = new Object();
	
	// constructor
	protected Server() throws RemoteException {
		super();
	}
	
	
	 // returns the last modified date of the file on the server
	public long getLastModified(String path) throws RemoteException{
		File f = new File(createPathName(dir, path));
		return f.lastModified();
	}
	
	/*
	 * Opens a session for the requested file and sends back the FileCache object
	 * to the client. Multiple sessions can be opened at a time
	 * 
	 */
	public FileCache openSession(FileCache file) throws RemoteException{
		String fileName = file.getServerFileName();
		String fileMode = file.getFileMode();

		if(!checkInServerDir(fileName)){
			// file is outside the server directory
			file.setErrorCode(FileHandling.Errors.EPERM); 
			return file;
		}
		
		// Open a File object
		File f = new File(dir, fileName);
		
		// If the file is a directory, return. The directory related checks would be done by the client.
		if (f.isDirectory()) {
			file.setDir(true);
			return file;
		}
		
		// File does not exist on server and is opened in READ/WRITE mode by the client.
		if (!f.exists() && ((fileMode.compareTo("READ") == 0) || (fileMode.compareTo("WRITE") == 0))) {
			file.setErrorCode(FileHandling.Errors.ENOENT);
			return file;
		}
		
		// Client wants to create a new file but the file already exists on the server
		if (f.exists() && ((fileMode.compareTo("CREATE_NEW") == 0))){
			file.setErrorCode(FileHandling.Errors.EEXIST);
			return file;
		}
		
		// Create the file on the server if it does not exist.
		if(!f.exists()){
			try {
				f.createNewFile();
			} catch (IOException e) {
				// Error creating new file
				file.setErrorCode(-1);
				return file;
			}
		}
		
		 /* The session should be opened only when the file is either:
		 * 	1. Not in use currently
		 *  2. Is in use but is only being sent to other clients, i.e. is not being updated
		 */
		synchronized(lock){
			// file is not in use
			if(!sessionFileLock.containsKey(fileName))
				sessionFileLock.put(fileName, 1);
			else{
				//block till the file is freed from writing by the other client
				while(sessionFileLock.get(fileName) == -1){
					// File is being updated
				}
				sessionFileLock.put(fileName, sessionFileLock.get(fileName)+1);			
			}
		}
		// Set the last modified value of the server file in the client file object
		file.setLastModifiedAt(f.lastModified());
		
		// Set the server file size on the client's file object
		long size = f.length();
		file.setFileSize(size);
		
		// Calculate the number of blocks required to transmit the file over the network.
		int nrOfBlocks = 0;
		// Local map to associate the block number with its size
		ConcurrentHashMap<Integer, Integer> blockSize = new ConcurrentHashMap<Integer, Integer>();
		if (size > MAX_BLOCK_SIZE) {
			while (size > 0) {
				nrOfBlocks++;
				if (size > MAX_BLOCK_SIZE) {
					size -= MAX_BLOCK_SIZE;
					blockSize.put(nrOfBlocks, MAX_BLOCK_SIZE);
				} else {
					blockSize.put(nrOfBlocks, (int) size);
					size = 0;
				}
			}
		} else {
			// if the file is of size 0, it has no blocks.
			nrOfBlocks = size == 0 ? 0 : 1;
			blockSize.put(nrOfBlocks, (int) size);
		}
		
		// Set number of blocks and block sizes on the client file object
		file.setNrOfBlocks(nrOfBlocks);
		file.setBlockSize(blockSize);
		return file;
	} //end openSession
	
	/*
	 * Close the session. Release the file from use
	 */
	public synchronized void closeSession(String path) throws RemoteException{
		int counter = sessionFileLock.get(path);
		counter -= 1;
		// if the file counter reaches 0, remove it from the map
		if (counter == 0)
			sessionFileLock.remove(path);
		else
			sessionFileLock.put(path, counter);
	}
	
	/*
	 * Get file from the root dir and send it to the proxy. The file block
	 * according to what has been requested would be sent to the proxy and not
	 * the whole file
	 */
	public synchronized byte[] getFile(int blockNumber, FileCache file) throws RemoteException {
		File f = new File(createPathName(dir, file.getServerFileName()));
		// File existence has already been checked in openSession().
		// File could not be unlinked till the file is sent
		
		try {		
			FileInputStream fIn = new FileInputStream(f);
			ConcurrentHashMap<Integer, Integer> blockSize = file.getBlockSize();
			// skip blocksize bytes for the blocks before requested block
			for (int i = 1; i < blockNumber; i++) {
				fIn.skip(blockSize.get(i));
			}
			byte[] bytes = new byte[blockSize.get(blockNumber)];
			fIn.read(bytes);
			fIn.close();
			return bytes;
		} catch (FileNotFoundException e) {
			// exception would not be raise
		} catch (IOException e) {
			// error reading
			return null;
		}

		return null;
	} //end getFile


	/*
	 * 	Opens a session on server for writing to the given file
	 */
	public synchronized void openSessionForWrite(String path) throws RemoteException {
		// the file to be written should not be in use currently. By use, it is meant
		// 1. file is not being sent to other clients
		// 2. file is not being updated currently
		while(sessionFileLock.containsKey(path)){
			//File currently under use. Wait
		}
		
		//File write lock obtained		
		sessionFileLock.put(path, -1);
		
		File file = new File(createPathName(dir, path));
		if(file.exists())
			// Delete original file before updating
			file.delete();
		
		file = new File(createPathName(dir, path));
		try {
			file.createNewFile();
		} catch (IOException e) {
			// unable to create new file
		}
		
	}

	/* 
	 * Update the file 
	 */
	public synchronized void writeFile(byte[] bytes, String path) throws RemoteException {
		try {
			FileOutputStream fOut = new FileOutputStream(createPathName(dir, path), true);
			fOut.write(bytes);
			fOut.close();
		} catch (FileNotFoundException e) {
			// exception would not raised. It is not possible to unlink the file while it is being updated
		} catch (IOException e) {
			// error in writing
		}	
	} 
	
	/*
	 * Close the session opened for writing. Update the last modified of the file
	 */
	public synchronized void closeSessionForWrite(String path) throws RemoteException {
		sessionFileLock.remove(path);
		
		File file = new File(createPathName(dir, path));
		file.setLastModified(System.currentTimeMillis());
	}

	/*
	 * Unlink the file from the server.
	 * Unlink should be done only when the file is not in use. 
	 * If the file is in use, wait to unlink
	 */
	public synchronized int unlink(String path){
		boolean inDir = checkInServerDir(path);
		if(inDir){
			while(sessionFileLock.containsKey(path)){
				// File currently under use. Wait
			}
			Path pathname = Paths.get(path);
			try {
				Files.delete(pathname);
			} catch (NoSuchFileException e) {
				// No such file
				return FileHandling.Errors.ENOENT;
			} catch (SecurityException | IOException e) {
				// Permission denied
				return FileHandling.Errors.EPERM;
			}
		}
		return 0;
	}
	
	/*
	 * Check the provided arguments
	 */
	private static void check_args(String[] args) {

		// Check arguments length
		if (args.length < 2) {
			// A total of 2 arguments are required
			System.exit(1);
		}

		// Check if port number is valid or not.
		try {
			Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			// Not a valid port number
			System.exit(1);
		}
		// Check and set directory.
		setDir(args[1]);
	}

	/*
	 * Set the server directory
	 */
	private static void setDir(String dirName) {
		String name = Paths.get(dirName).toAbsolutePath().normalize().toString();
		File f = new File(name);
		if (!f.exists()) {
			//Directory does not exist
			System.exit(1);
		} else if (!f.isDirectory()) {
			// Not a directory
			System.exit(1);
		}
		dir = name;
	}
	
	// create the file pathname relative to the directory
	private static String createPathName(String dir, String path) {
		
		// If absolute file name is given (/foo/filename), append as such to dir 
		// else add '/' before appending
		 
		String fPath = path.startsWith("/") ? dir + path : dir + '/' + path;
		Path p = Paths.get(fPath).normalize();
		return p.toString();
	}

	/*
	 * Check whether the requested file is in the server directory or not
	 */
	private boolean checkInServerDir(String fileName) {
		String filePath = createPathName(dir, fileName);
		
		// Get the absolute server directory
		Path dirAbsPath = Paths.get(dir).normalize().toAbsolutePath();
		// Get the absolute file path
		Path fileAbsPath = Paths.get(filePath).normalize().toAbsolutePath();
		return fileAbsPath.toString().contains(dirAbsPath.toString());
	}
	
	public static void main(String[] args) {

		// Check for arguments validity
		check_args(args);
		
		Server svr = null;
		int port = Integer.parseInt(args[0]);
		try {
			//create the RMI registry if it doesn't exist.
			LocateRegistry.createRegistry(port);
		}
		catch(RemoteException e) {
			System.err.println("Failed to create the RMI registry " + e);
			System.exit(1);
		}
		
		try {
			svr = new Server();
		} catch (RemoteException e) {
			System.err.println("Failed to create server " + e);
			System.exit(1);
		}
		try {
			Naming.rebind(String.format("//127.0.0.1:%s/ServerService", args[0]),
					svr);
		} catch (RemoteException e) {
			System.err.println("Failed to bind " + e);
			System.exit(1);
		} catch (MalformedURLException e) {
			System.err.println("URL not correct " + e);
			System.exit(1);
		}

		System.err.format("Server: Server Bound\n");
	} //end main
} //end class
