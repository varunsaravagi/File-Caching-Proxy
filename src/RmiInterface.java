
import java.rmi.*;

/**
 * @author vsaravag
 * RMI interface for the server.
 */

public interface RmiInterface extends java.rmi.Remote{
	
	public FileCache openSession(FileCache file) throws RemoteException;
	public void closeSession(String path) throws RemoteException;
	public byte[] getFile(int blockNumber, FileCache file) throws RemoteException;
	public void writeFile(byte[] bytes, String path) throws RemoteException;
	public void openSessionForWrite(String path) throws RemoteException;	
	public void closeSessionForWrite(String origFile) throws RemoteException;
	public int unlink(String path) throws RemoteException;
	public long getLastModified(String path) throws RemoteException;
}
