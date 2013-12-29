import java.io.*;
import java.util.HashMap;

public class Library 
{
	public static long netSpeed;
	public static long latency;
	public static long sendOverhead;
	public static long recvOverhead;
	public static long jobProcTime;
	public static long kvsProcTime;
	
	public static int preNoJobFinished;
	public static int numJobFinished;
	public static int numAllJobs;
	public static long numAllMsg;
	
	public static HashMap<String, Job> jobMetaData;
	
	public static byte[] serialize(Object obj)
	{
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    try
	    {
	    	ObjectOutputStream oos = new ObjectOutputStream(baos);
	    	oos.writeObject(obj);
	    }
	    catch (IOException e)
	    {
	    	e.printStackTrace();
	    }
	    return baos.toByteArray();
	}
	
	public static  long getCommOverhead(int msgSize)
	{
		return msgSize * 8L * 1000000L / Library.netSpeed + Library.latency;
	}
}