import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author vsaravag
 * Class to handle the file in the cache
 * Each file in the cache will have an associated FileCache object
 */

public class FileCache implements Serializable, Cloneable {
	
	private String fileName;		
	private String serverFileName;		// the name with which the file is stored on server
	private int errorCode = 0;			// error code returned by the server
	private String fileMode;			// mode in which the file is opened
	private long fileSize;				// size of the file
	private int nrOfBlocks;				// number of blocks required to get the file
	private long lastModifiedAt;		// time the file was last modified on server
	private boolean isDir = false;		// is file a directory
	private boolean err = false;		// any error while writing to the file
	// map to associate blocks with their size
	private ConcurrentHashMap<Integer, Integer> blockSize = new ConcurrentHashMap<Integer, Integer>();
	
	// Constructor. Sets the file name, server file name and mode
	public FileCache(String path, String mode){
		Path p = Paths.get(path).normalize();
		this.fileName = p.toString();
		this.setServerFileName(p.toString());
		this.fileMode = mode;
	}
	
	// make the class cloneable
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	// get the file path
	public String getFilePath(){
		return fileName;
	}
	
	// set the file path
	public void setFilePath(String path){
		fileName = path;
	}
	
	// get the error code
	public int getErrorCode() {
		return errorCode;
	}

	// set the error code
	public void setErrorCode(int error){
		this.errorCode = error;
	}
	
	// get the file mode
	public String getFileMode(){
		return fileMode;
	}

	// get the file size
	public long getFileSize() {
		return fileSize;
	}

	// set the file size
	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	// get the number of blocks
	public int getNrOfBlocks() {
		return nrOfBlocks;
	}

	// set the number of blocks
	public void setNrOfBlocks(int nrOfBlocks) {
		this.nrOfBlocks = nrOfBlocks;
	}

	// get the blockSize map
	public ConcurrentHashMap<Integer, Integer> getBlockSize() {
		return blockSize;
	}

	// set the blockSize map
	public void setBlockSize(ConcurrentHashMap<Integer, Integer> blockSize) {
		this.blockSize = blockSize;
	}

	// get the last modified
	public long getLastModifiedAt() {
		return lastModifiedAt;
	}

	// set the last modified
	public void setLastModifiedAt(long lastModifiedAt) {
		this.lastModifiedAt = lastModifiedAt;
	}

	// returns the isDir variable
	public boolean isDir() {
		return isDir;
	}

	// set isDir
	public void setDir(boolean isDir) {
		this.isDir = isDir;
	}

	// return isErr
	public boolean isErr() {
		return err;
	}

	// set isErr
	public void setErr(boolean err) {
		this.err = err;
	}

	// get server file name
	public String getServerFileName() {
		return serverFileName;
	}

	// set server file name
	public void setServerFileName(String serverFileName) {
		this.serverFileName = serverFileName;
	}

}
