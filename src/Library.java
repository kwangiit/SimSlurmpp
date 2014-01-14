import java.io.*;
import java.util.*;

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
	public static int numJobsPerCtrl;
	public static long numAllMsg;
	public static long numInsertMsg;
	public static long numLookupMsg;
	public static long numCswapMsg;
	public static long numCallbackMsg;
	
	public static String[] memList;
	public static ArrayList<String> workload;
	
	public static HashMap<String, Job> jobMetaData;
	
	public static BufferedWriter bwThroughput;
	public static BufferedWriter bwTaskDetail;
	public static BufferedWriter bwStatInfo;
	
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
	
	public static void outputTaskDetail(int numCtrls, int partSize, int numJobsPerPart)
	{
		try
		{
			bwTaskDetail.write("JobId\tStartTime\tSubmissionTime\t" +
				"ExecuteTime\tFinishTime\tResultBackTime\r\n");
			for (int i = 0; i < numCtrls; i++)
			{
				String ctrlId = "node-" + Integer.toString(i * partSize);
				for (int j = 0; j < numJobsPerPart; j++)
				{
					String jobId = ctrlId + " " + Integer.toString(j);
					Job job = jobMetaData.get(jobId);
					long startTime;
					long submitTime;
					long exeTime;
					long finTime;
					long backTime;
					bwTaskDetail.write(jobId + "\t" + job.startTime + "\t" + job.submitTime + "\t" + 
					               job.exeTime + "\t" + job.finTime + "\t" + job.backTime + "\r\n");
				}
			}
			bwTaskDetail.flush();
			bwTaskDetail.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}