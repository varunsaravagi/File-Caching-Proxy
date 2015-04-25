

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;


class Proxy {

	// Variables at the proxy level.
	
	// rmi object to connect to the server
	private static RmiInterface svr = null;
	// cache directory
	private static String dir = null;
	// instance of the CacheMgr class. One proxy would have only one instance
	private static CacheMgr cacheMgr = new CacheMgr();

	// Maximum file size which can be transferred/received at a time
	private static final int MAX_BLOCK_SIZE = 1024 * 1024;
	// object to handle locking
	private static Object lock = new Object();

	public static class FileHandler implements FileHandling {

		/*----- The static variables are meant to be shared across all the clients-----*/

		// File descriptor to be given to each client. Has to be unique across clients.
		private static int uniqueFd = 10;
		// Map to store the File Descriptor against the associated FileCache object
		private static ConcurrentHashMap<Integer, FileCache> fileCacheFdMap = new ConcurrentHashMap<Integer, FileCache>();	
		
		/*----- The below variables are private to a client and not shared among different clients -----*/

		// Map to store the RandomFileAccess object against the File Descriptor
		private ConcurrentHashMap<Integer, RandomAccessFile> fileFdMap = new ConcurrentHashMap<Integer, RandomAccessFile>();
		// ArrayList to store the File Descriptors which are opened for a directory
		private ArrayList<Integer> fdDir = new ArrayList<Integer>();
		// ByteBuffer to wrap the content of the Byte[]
		private ByteBuffer content;

		/*
		 * The method does the following main things (in sequence):
		 * 1.	Opens a session on the server for the requested file.
		 * 2.	Checks for file in the cache and gets the file from the
		 * 		server if required.
		 * 3.	Returns a file descriptor
		 */
		public int open(String path, OpenOption o) {

			String mode = o.toString();
			
			// Get an instance of FileCache object
			FileCache file = new FileCache(path, o.toString());
			
			// Open a session on the server for the file and get the required attributes
			try {
				file = svr.openSession(file);
			} catch (RemoteException e) {
				return Errors.EBUSY; 
			}

			/*----- START: Check for errors -----*/ 
			
			// File does not exists on server
			if (file.getErrorCode() == Errors.ENOENT) 
				return Errors.ENOENT;

			// File is opened in CREATE_NEW mode but the file exists on server
			if (file.getErrorCode() == Errors.EEXIST) 
				return Errors.EEXIST;

			// File is accessed outside the server directory
			if (file.getErrorCode() == Errors.EPERM) 
				return Errors.EPERM;

			/*------ END: Check for errors. No errors -----*/	

			
			// If the file being requested is a directory and is opened in READ
			// mode, return a file descriptor. Directory is not created in the cache
			if (file.isDir() && mode.compareTo("READ") == 0) {
				
				// Since file descriptor variable is shared across clients, the
				// operation needs to be made atomic so as to make sure that the 
				// fd generated for the file is the one which is added to the list and
				// returned.
				synchronized (lock) {
					uniqueFd++;
					fdDir.add(uniqueFd);
					return uniqueFd;
				}
			} else if (file.isDir()) {
				// directory is requested to be opened in NON-READ mode, return error
				return Errors.EISDIR;
			}

			/*----- START: Get lock on file -------*/
			 
			// Get exclusive lock on the file before comparing the file in cache
			// to the file on server. This is to make sure that no two clients on
			// a Proxy are fetching the same file from the server simultaneously. 
			Path p = Paths.get(createPathName(dir, file.getFilePath()));
			Path pDir = p.getParent();
			try {
				Files.createDirectories(pDir);
			} catch (IOException e1) {
				//Error creating directory structure
				return Errors.EBADF; //may be not the best error code.
			}
			
			File f = new File(p.toString());
			RandomAccessFile rf = null;
			FileLock fLock = null;
			try {
				// If the code comes here and the file is to be opened in
				// CREATE_NEW, we can create the file while getting the lock itself.
				rf = new RandomAccessFile(f, "rw");
				FileChannel fChannel = rf.getChannel();

				// Loop till the client gets a lock on the file. If the client does not get a lock,
				// then the file is currently being obtained by another client
				while (true) {
					try {
						fLock = fChannel.tryLock();
						break;
					} catch (OverlappingFileLockException e) {
						// Lock not found
					}
				}

			} catch (FileNotFoundException e) {
				// This exception would never be raised. File has been created
				// already.
			} catch (IOException e) {
				// Error creating lock
				return -1;
			}
			
			/*----- END: Get lock on file. Lock obtained -------*/
			
			/*----- START: Check and get file from server -------*/
			 
			// If the file exists on the cache but is not the latest version OR the
			// file does not exist on the cache; in both the cases, fetch the
			// file from the server.
			 
			FileCache cachedFile = null;
			boolean getFromServer = false;
			// Check if the file exists in cache or not
			if ((cachedFile = cacheMgr.isFileInCache(path)) != null) {
				// File exists in cache.
				// Compare the last modified date of the file in cache with the 
				// last modified date of the file on server (file object's last modified date)
				if (cachedFile.getLastModifiedAt() != file.getLastModifiedAt())
					// File in cache is not the latest version.
					getFromServer = true;
				else{
					// set the filepath of the current cache object to the filepath of the file in cache.
					// File in cache might have a different name than the one requested by the client
					file.setFilePath(cachedFile.getFilePath());
				}
			} else
				// File does not exist in the cache.
				getFromServer = true;

			if (getFromServer) {
				// Get file from server
				int nrOfBlocks = file.getNrOfBlocks();
				if (nrOfBlocks == 0) {
					// New file is to be created. It has already been created while acquiring lock.
					// Since it's a new file, it would not take any space in the
					// cache till it is written to. So no need to check for space in the cache
				} else {
					// Check cache for space. This process needs to be synchronized so that multiple
					// clients don't update the cache space simultaneously
					synchronized (lock) {
						
						boolean hasSpace = cacheMgr.checkAndMakeSpace(
								file.getFileSize(), path, path, false);
						// Cache does not have space.
						if (!hasSpace) {
							// Cache does not have space. Release the lock on the file
							try {
								if (f.length() == 0)
									// the file was created while acquiring lock. Delete it.
									f.delete();
								fLock.release();
								rf.close();
							} catch (IOException e) {
								// Error in releasing lock
								return -1;
							}
							// Close session on the server
							try {
								svr.closeSession(file.getServerFileName());
							} catch (RemoteException e) {
								return Errors.EBUSY;
							}
							return Errors.ENOMEM;
						}
						
						// Cache has space
						try {
							String newName = path;
							if (cacheMgr.fileInUse(path) > 0) {
								// If the file is in use currently by another client,
								// create a separate copy in cache.
								newName = createNewName(path, "r");
								f = new File(createPathName(dir, newName));
							}
							else
							{	
								// if the file is not in use, delete it before writing to it.
								// if not deleted, it is probable that the resulting cache file has 
								// a mix of the old and the new contents.
								f = new File(createPathName(dir, path));
								f.delete();
							}
							
							// Get the file in blocks
							for (int i = 1; i <= nrOfBlocks; i++) {
								byte[] bytes;
								bytes = svr.getFile(i, file);
								FileOutputStream fOut = new FileOutputStream(f, true);
								fOut.write(bytes);
								fOut.close();
							}
							// update the filename to the new name (if any)
							file.setFilePath(newName);
							// add the latest version of the file in cache
							cacheMgr.setLatestVersion(path, file);
						} catch (RemoteException e) {
							return Errors.EBUSY;
						} catch (FileNotFoundException e) {
							// This exception would never be raised
						} catch (IOException e) {
							// Error writing to file
							return -1;
						} //end try
					} //end synchronized
				} //end if(nrOfBlocks)
			} //end if(getFromServer) 
			
			/*----- END: Check and get file from server -------*/
			
			// Release the lock on the file
			try {
				fLock.release();
				rf.close();
			} catch (IOException e) {
				// Error releasing lock
				return -1;
			}
			// Close session on the server
			try {
				svr.closeSession(file.getServerFileName());
			} catch (RemoteException e) {
				//Unable to connect to remote function
				return Errors.EBUSY;
			}
			
			/*------ START: Open the file and send file descriptor ------*/
			// Create a copy of the file object
			FileCache newFile = file;
			try {
				// check whether the Option sent is in the enums or not.
				OpenOption.valueOf(mode);

				rf = null;
				if (mode.compareTo("CREATE_NEW") == 0
						|| mode.compareTo("CREATE") == 0
						|| mode.compareTo("WRITE") == 0) {

					// create a private copy in the cache and open that
					String privateFileName = createNewName(file.getFilePath(), "w");
					synchronized (lock) {
						// Check if the cache has space for the private copy.
						boolean hasSpace = cacheMgr.checkAndMakeSpace(
								file.getFileSize(), path, privateFileName, true);
						if (!hasSpace) 
							// cache does not has space
							return Errors.ENOMEM;
						
						try {
							File sFile = new File(createPathName(dir, path));
							File dFile = new File(createPathName(dir, privateFileName));
							dFile.createNewFile();
							
							cacheMgr.createPrivateCopy(sFile, dFile);
							rf = new RandomAccessFile(createPathName(dir,
									privateFileName), "rw");
							// (Self desing decision)
							// Mark the master copy of the file as MRU. 
							// (since it was used to create the private copy)
							cacheMgr.markFileAsMRU(file);							
							
							// create a new FileCache object for the private copy
							newFile = (FileCache) file.clone();
							newFile.setFilePath(privateFileName);
						} catch (IOException e) {
							// Error creating private copy
							return -1;
						} catch (CloneNotSupportedException e) {
							e.printStackTrace();
						}
					}
				} else {
					// open file only for read only access
					rf = new RandomAccessFile(createPathName(dir, newFile.getFilePath()), "r");
				}

				// This ensures that the generated fd is only returned as ++ is
				// not an atomic operation
				synchronized (lock) {
					uniqueFd++;
					return setMaps(uniqueFd, newFile, rf);
				}
			} catch (FileNotFoundException e) {
				// File does not exist
				return Errors.ENOENT;
			} catch (IllegalArgumentException e) {
				// Invalid arguments
				return Errors.EINVAL;
			}
		} //end open

		public int close(int fd) {
			// fd is a directory
			if (fdDir.contains(fd)) {
				fdDir.remove(fdDir.indexOf(fd));
				return 0;
			}
			
			// fd was not opened by the client
			if (!fileFdMap.containsKey(fd)) 
				return Errors.EBADF;

			FileCache file = fileCacheFdMap.get(fd);
			String fileName = file.getFilePath();
			
			// if the file was opened in non read mode, send the file to server only (if write succeeded)
			// and delete the private copy created.
			if (file.getFileMode().compareTo("READ") != 0) {
				if (!file.isErr())
					sendFileToServer(fileName, file.getServerFileName());
				
				File f = new File(createPathName(dir, fileName));
				f.delete(); // delete the private copy
			
			} else {
				// The file was opened in READ mode. Mark it as MRU.
				// This needs to be an atomic operation and should be done one
				// client at a time.
				synchronized (lock) {
					// Mark the file as MRU only if it is not being used by anyone else 
					// other than this client
					if(cacheMgr.fileInUse(file.getFilePath()) <= 1)
						cacheMgr.markFileAsMRU(file);
				}
			}
			// close/remove the entries from the map
			try {
				RandomAccessFile rf = fileFdMap.get(fd);
				rf.close();
				fileFdMap.remove(fd);
				fileCacheFdMap.remove(fd);
				cacheMgr.removeFileInUse(fileName);
			} catch (IOException e1) {
				// Error closing channel
				return -1;
			}
			return 0;
		} // end close

		public long write(int fd, byte[] buf) {
			long bytesWritten = -1;
			
			// fd is a directory
			if (fdDir.contains(fd)) 
				return Errors.EISDIR;
			
			// fd was not opened by this client
			if (!fileFdMap.containsKey(fd)) 
				return Errors.EBADF;
			
			// wrap the byte[] into a byte buffer
			content = ByteBuffer.wrap(buf);
			RandomAccessFile rf = fileFdMap.get(fd);
			try {
				// check cache space before writing
				synchronized (lock) {
					FileCache file = fileCacheFdMap.get(fd);
					boolean hasSpace = cacheMgr.checkAndMakeSpace(buf.length,
							file.getServerFileName(),
							file.getFilePath(), true);
					if (!hasSpace) {
						// cache does not have space. Mark it in the file object
						// return 0. Nothing was written
						file.setErr(true);
						return 0;
					}
					bytesWritten = rf.getChannel().write(content);
					fileFdMap.replace(fd, rf);	
				}
			} catch (IOException e) {
				// Error getting File Channel
				return -1;
			} catch (NonWritableChannelException e) {
				// File was not opened for write
				return Errors.EBADF;
			}
			return bytesWritten;
		} //end write

		public long read(int fd, byte[] buf) {
			long bytesRead = -1;
			
			// fd is a directory
			if (fdDir.contains(fd))
				return Errors.EISDIR;
			
			// fd was not opened by this client
			if (!fileFdMap.containsKey(fd))
				return Errors.EBADF;

			content = ByteBuffer.allocate(buf.length);
			RandomAccessFile rf = fileFdMap.get(fd);

			try {
				// 0 bytes read if the end of file is reached
				bytesRead = rf.getFilePointer() == rf.length() ? 0 : rf.read(buf);
				fileFdMap.replace(fd, rf);
			} catch (IOException e) {
				// Error reading
				return -1;
			}
			return bytesRead;
		} //end read

		public long lseek(int fd, long pos, LseekOption o) {
			/*
			 * Advances the file pointer by the pos given. To note that pos is a
			 * signed value whereas file seek only accepts non-negative values.
			 * Appropriate conversion needs to be done.
			 */
			
			// fd is a dir
			if (fdDir.contains(fd)) 
				return Errors.EISDIR;
			
			// fd is not opened by the client
			if (!fileFdMap.containsKey(fd)) 
				return Errors.EBADF;

			RandomAccessFile rf = fileFdMap.get(fd);
			long desired_pos = 0;
			try {
				switch (o.toString()) {
				case "FROM_CURRENT":
					// Calculate the desired position,
					desired_pos = rf.getFilePointer() + pos;
					if (desired_pos < 0) {
						//Negative offset
						return Errors.EINVAL;
					} else {
						rf.seek(0);
						rf.seek(desired_pos);
						fileFdMap.replace(fd, rf);
					}
					break;

				case "FROM_END":
					desired_pos = rf.length() + pos;
					if (desired_pos < 0) {
						//Negative offset
						return Errors.EINVAL;
					} else {
						rf.seek(0);
						rf.seek(desired_pos);
						fileFdMap.replace(fd, rf);
					}
					break;

				case "FROM_START":
					desired_pos = pos;
					if (desired_pos < 0) {
						//Negative offset
						return Errors.EINVAL;
					} else {
						rf.seek(0);
						rf.seek(desired_pos);
						fileFdMap.replace(fd, rf);
					}
					break;

				default:
					return Errors.EINVAL;
				}
				return rf.getFilePointer();
			} catch (IOException e) {
				// error seeking
				return Errors.EBADF;
			}
		} //end lseek

		public int unlink(String path) {
			// unlink happens only on the server side
			int err;
			try {
				err = svr.unlink(path);
			} catch (RemoteException e) {
				//Error connecting to server
				return Errors.ENOENT;
			}
			return err;
		}

		public void clientdone() {
			return;
		}
		
		// creates a new name for the given path
		private String createNewName(String path, String mode) {
			// append before the extension
			int i = path.contains(".") ? path.lastIndexOf(".") : path.length();
			return path.substring(0, i) + Thread.currentThread().getId() + "_"+mode
					+ path.substring(i);
		}
		
		// sets all the maps and returns the fd
		private synchronized int setMaps(int fd, FileCache file, RandomAccessFile rf) {
			fileFdMap.put(fd, rf);
			fileCacheFdMap.put(fd, file);
			cacheMgr.addFileInUse(file.getFilePath());
			return fd;
		}
		
		// create pathname w.r.t to the given directory. Normalize the pathname
		private String createPathName(String dir, String path) {
			/*
			 * creates file pathname and return. if absolute file name is given
			 * (/foo/filename), append as such to dir else add '/' before
			 * appending
			 */
			String fPath = path.startsWith("/") ? dir + path : dir + '/' + path;
			Path p = Paths.get(fPath).normalize();
			return p.toString();
		}

		// Send file to the server
		private void sendFileToServer(String path, String serverPath) {
			try {
				File file = new File(createPathName(dir, path));
				long size = file.length();
				int no_of_blocks = 0;
				
				// initialize a map to store the block against its size
				ConcurrentHashMap<Integer, Integer> blockSize = new ConcurrentHashMap<Integer, Integer>();
				if (size > MAX_BLOCK_SIZE) {
					while (size > 0) {
						no_of_blocks++;
						if (size > MAX_BLOCK_SIZE) {
							size -= MAX_BLOCK_SIZE;
							blockSize.put(no_of_blocks, MAX_BLOCK_SIZE);
						} else {
							blockSize.put(no_of_blocks, (int) size);
							size = 0;
						}
					}
				} else {
					no_of_blocks = 1;
					blockSize.put(no_of_blocks, (int) size);
				}

				int skipBytes = 0;
				synchronized (lock) {
					// Open a session for write on the server. The file name on 
					// the server would be the one the client requested initially.
					svr.openSessionForWrite(serverPath);
					// send file in chunks
					for (int i = 1; i <= no_of_blocks; i++) {
						// skip blocksize bytes if more than one block is to be sent
						if (i != 1)
							skipBytes += blockSize.get(i - 1);
						
						FileInputStream fIn = new FileInputStream(createPathName(dir, path));
						fIn.skip(skipBytes);
						byte[] bytes = new byte[blockSize.get(i)];
						fIn.read(bytes);
						fIn.close();
						// write file to the server
						svr.writeFile(bytes, serverPath);
					}
					// close the session for write on the server.
					svr.closeSessionForWrite(serverPath);
				} //end synchronized
			} catch (FileNotFoundException e) {
				// this exception would not be raised.
			} catch (IOException e) {
				// Error writing
			}
		}

	}

	private static class FileHandlingFactory implements FileHandlingMaking {
		public FileHandling newclient() {
			return new FileHandler();
		}
	}

	public static void main(String[] args) throws IOException {
		int err = 0;
		// check the given arguments and set the required variables
		err = check_args(args);
		if (err < 0) {
			// Invalid arguments
			System.exit(1);
		}
		// connect to the server
		connect_to_server(args[0], args[1]);

		(new RPCreceiver(new FileHandlingFactory())).run();
	}
	
	// Check the given arguments
	public static int check_args(String[] args) {
		int err = 0;
		// Check arguments length
		if (args.length < 4) {
			// A total of 4 arguments are required
			System.exit(1);
		}

		// Check port number
		try {
			Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			//Not a valid port number
			System.exit(1);
		}

		// Set cache directory.
		err = cacheMgr.setCacheDir(args[2]);
		if (err < 0)
			System.exit(1);
		Proxy.dir = cacheMgr.getCacheDir();

		// Set cache size
		try {
			cacheMgr.setCacheSize(Long.parseLong(args[3]));
		} catch (NumberFormatException e) {
			//Invalid Cache size
			System.exit(1);
		}
		return err;
	}

	public static void connect_to_server(String ip, String port) {
		try {
			svr = (RmiInterface) Naming.lookup(String.format(
					"//%s:%s/ServerService", ip, port));
		} catch (RemoteException e) {
			System.err.println("Failed to connect to Server " + e);
			System.exit(1);
		} catch (NotBoundException e) {
			System.err.println("Not Bound " + e);
			System.exit(1);
		} catch (MalformedURLException e) {
			System.err.println("Invalid URL " + e);
			System.exit(1);
		}
		
		if (svr == null) {
			System.err.println("server is null");
			System.exit(1);
		}
	}

}
