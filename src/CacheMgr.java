import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author vsaravag
 * This class manages the Cache folder. Each file in the cache has an associated FileCache object
 * 
 * 1. Keeps track of different versions of the files
 * 2. Manages LRU cache replacement policy
 * 3. Keeps track of the files currently in use
 * 
 */

public class CacheMgr {
	private String cacheDir;				//cache directory
	private long cacheSize;					//cache size
	private Object lock = new Object();		//object to handle locking
	
	// Linked list to handle LRU
	// Storage: 
	// ---- cold-> |0|1|2|3|......|n| <-warm
	private LinkedList<FileCache> fileUseList = new LinkedList<FileCache>();
	
	// Map to store the different versions of a file against the master file name (server file name)
	// Latest version is at the tail, oldest at the head
	private ConcurrentHashMap<String, LinkedList<FileCache>> fileVersions = new ConcurrentHashMap<String, LinkedList<FileCache>>();
	
	// Map to store the files which are currently in use against their FileCache object
	private ConcurrentHashMap<String, Integer> filesInUse = new ConcurrentHashMap<String, Integer>();
	
	// constructor
	public CacheMgr(){
		
	}
	
	// add the given FileCache object as the latest version of the given file path.  
	public synchronized void setLatestVersion(String path, FileCache file){
		LinkedList<FileCache> fileCacheVersions = new LinkedList<FileCache>();
		if (fileVersions.containsKey(path)){
		// Get all the versions of the file which exist in the cache
			fileCacheVersions = fileVersions.get(path);
		}
		// add the latest version to the end of the file
		fileCacheVersions.add(file);			
		// update the map with the new list
		fileVersions.put(path, fileCacheVersions);
	}
	
	// return the cache dir
	public String getCacheDir() {
		return cacheDir;
	}

	// set the cache directory. 
	// return 0 if success, else -1
	public synchronized int setCacheDir(String cacheDir) {
		File f = new File(createPathName(cacheDir, ""));
		if (!f.exists()) {
			if (f.mkdir()){
				//Cache directory created
			}
			else {
				//Failed to create cache directory
				return -1;
			}
		} else if (!f.isDirectory()) {
			//Not a directory
			return -1;
		}

		// get normalized absolute path and set the cache directory
		this.cacheDir = Paths.get(cacheDir).toAbsolutePath().normalize().toString();
		return 0;
	}
	
	
	// check if the file is in the cache
	// return the latest version of the file if found.
	public synchronized FileCache isFileInCache(String fileName){
		if (fileVersions.containsKey(fileName)) {
			try {
				
				int size = fileVersions.get(fileName).size();
				int i = size - 1;
				LinkedList<FileCache> versions = fileVersions.get(fileName);
				while(i >= 0){
					// only return the master copy of the file.
					// private copy created for write should not be returned, because they would be
					// deleted after they are written to.
					String latestFile = versions.get(i).getFilePath();
					if (latestFile.endsWith("_w")){
						i--;
						continue;
					}
					else
						break;
				}
				// version found
				if (i>=0)
					return versions.get(i);
				else
					return null;
			} catch (NoSuchElementException e) {
				// file has no versions
				return null;
			}
		} else
			// file has no versions
			return null;
	}
	
	// get the size of the cache
	public long getCacheSize() {
		return cacheSize;
	}
	
	// set the size of the cache
	public void setCacheSize(long cacheSize) {
		this.cacheSize = cacheSize;
	}
	
	// get the total used space in the cache.
	// this is the sum of all the files in the cache
	public synchronized long getUsedSpace(){
		File[] files = new File(cacheDir).listFiles();
		long size = 0L;
		for(int i=0;i<files.length;i++)
			size+= files[i].length();
		return size;
	}

	// remove the file from the use list. Decrease the counter by 1.
	// if counter reaches 0, remove the entry for the file
	public synchronized void removeFileInUse(String path){
		int counter = filesInUse.get(path);
		counter -= 1;
		if (counter == 0){
			filesInUse.remove(path);
			return;
		}
		filesInUse.put(path, counter);
	}
	
	// remove file from the LRU
	public synchronized void removeFileFromLRU(FileCache file){
		fileUseList.remove(file);
	}
	
	
	// mark file as MRU (most recently used).
	// Do the operation only if the file is not in use currently
	public synchronized void markFileAsMRU(FileCache file){
		int size = fileUseList.size();boolean flag = false;
		for(int i = 0; i < size; i++){
			//file exists in LRU
			if (fileUseList.get(i).getFilePath().compareTo(file.getFilePath()) == 0){
				// remove the file from LRU
				fileUseList.remove(i);
				// add the file to the end of the list
				fileUseList.add(file);
				flag = true;
				break;
			}
		}
		// LRU list is empty or file does not exist in LRU. Add it to the end of the list
		if (!flag)
			fileUseList.add(file);
	}
	
	
	// delete the file from the cache
	public synchronized void deleteFile(String path){
		File f = new File(createPathName(cacheDir, path));
		f.delete();
	}
	
	
	// check if the file is currently in use or not.
	public synchronized int fileInUse(String path){
		if (filesInUse.containsKey(path))
			return filesInUse.get(path);
		else
			return 0;
	}
	
	
	// add the file to the usage list. Increase counter by 1
	public synchronized void addFileInUse(String path){
		if(!filesInUse.containsKey(path))
			filesInUse.put(path, 1);
		else
			filesInUse.put(path, filesInUse.get(path)+1);
	}
	
	
	/**
	 * check and make space in the cache
	 * @param reqSpace		: the space required by the file in the cache
	 * @param path			: file name
	 * @param filesInUse	: files currently in use by the Proxy. These files cannot be evicted
	 * @param privateCopy	: file is a private copy	
	 * @return				: true if the space was made in the cache. False otherwise
	 * 
	 */
	public synchronized boolean checkAndMakeSpace(long reqSpace, String path, String newPath, boolean privateCopy){
		// total space required by the file exceeds the cache size. Return false.
		if (cacheSize < reqSpace){
			return false;
		}
		
		synchronized (lock) {
			int index = 0;
			long freeSpace = cacheSize-getUsedSpace();
			
			while(freeSpace < reqSpace){
				// cache has some free space
				// the whole LRU has been scanned but still space is not made. Return false
				if (index > fileUseList.size() - 1){
					return false;
				}
				
				// get the LRU file from the list. This would be at index 0
				FileCache lruFile = fileUseList.get(index);
				
				// if the LRU file is the master copy and the given file is a private copy, do not delete it
				// This is because the write from master copy is under progress
				if (lruFile.getFilePath().compareTo(path) == 0 && privateCopy){
					index++;
					continue;
				}
				
				// if the LRU file is not in use currently, delete it and free some space in the cache
				if(!filesInUse.contains(lruFile.getFilePath())){	
					// remove file from LRU list
					fileUseList.remove(lruFile);
					File f = new File(createPathName(cacheDir, lruFile.getFilePath()));
					long spaceFreed = f.length();
					freeSpace += spaceFreed;
					f.delete();
					// Remove the LRU file from the list of versions maintained for the file
					fileVersions.get(lruFile.getServerFileName()).remove(lruFile);
				}
				else{
					// file is currently in use. Try next LRU file
					index++;
				}	
			}
		}
		return true;
	}
	
	// create a private copy for the given source file
	public synchronized void createPrivateCopy(File source, File dest) throws FileNotFoundException, IOException {
	    FileInputStream is = null;
	    FileOutputStream os = null;
	    is = new FileInputStream(source.getAbsolutePath());
	    byte[] buffer = new byte[1024*1024];
	    int length;
	    os = new FileOutputStream(dest.getAbsolutePath(),true);
	    while ((length = is.read(buffer)) > 0) {
	    	os.write(buffer, 0, length);
	    }
	    is.close();
    	os.close();
	}
	
	
	/*
	 * creates file pathname and return normalized pathname. if absolute file name is given
	 * (/foo/filename), append as such to dir else add '/' before
	 * appending
	 */
	private String createPathName(String dir, String path) {
		
		String fPath = path.startsWith("/") ? dir + path : dir + '/' + path;
		Path p = Paths.get(fPath).normalize();
		return p.toString();
	}

}
