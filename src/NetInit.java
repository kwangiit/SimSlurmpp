import java.util.*;
import peersim.core.*;
import peersim.config.Configuration;
import java.io.*;

public class NetInit implements Control 
{
	private static final String PAR_PROT = "protocol";
	private static final String PAR_PARTITIONSIZE = "partSize";
	private static final String PAR_NETSPEED = "netSpeed";
	private static final String PAR_LATENCY = "latency";
	private static final String PAR_SENDOVERHEAD = "sendOverhead";
	private static final String PAR_RECVOVERHEAD = "recvOverhead";
	private static final String PAR_JOBPROCTIME = "jobProcTime";
	private static final String PAR_KVSPROCTIME = "kvsProcTime";
	private static final String PAR_WORKLOADFILE = "workloadFile";
	
	private int pid;
	private int partSize;
	private long netSpeed;
	private long latency;
	private long sendOverhead;
	private long recvOverhead;
	private long jobProcTime;
	private long kvsProcTime;
	private String workloadFile;
	
	public NetInit(String prefix)
	{
		pid = Configuration.getPid(prefix + "." + PAR_PROT);
		partSize = Configuration.getInt(prefix + "." + PAR_PARTITIONSIZE);
		netSpeed = Configuration.getLong(prefix + "." + PAR_NETSPEED);
		latency = Configuration.getLong(prefix + "." + PAR_LATENCY);
		sendOverhead = Configuration.getLong(prefix + "." + PAR_SENDOVERHEAD);
		recvOverhead = Configuration.getLong(prefix + "." + PAR_RECVOVERHEAD);
		jobProcTime = Configuration.getLong(prefix + "." + PAR_JOBPROCTIME);
		kvsProcTime = Configuration.getLong(prefix + "." + PAR_KVSPROCTIME);
		workloadFile = Configuration.getString(prefix + "." + PAR_WORKLOADFILE);
	}
	
	public void initLibrary()
	{
		Library.netSpeed = netSpeed;
		Library.latency = latency;
		Library.sendOverhead = sendOverhead;
		Library.recvOverhead = recvOverhead;
		Library.jobProcTime = jobProcTime;
		Library.kvsProcTime = kvsProcTime;
		
		Library.preNoJobFinished = 0;
		Library.numJobFinished = 0;
		//public static int numAllJobs;
		Library.numAllMsg = 0;
		Library.jobMetaData = new HashMap<String, Job>();
	}
	
	public void initPeer()
	{
		ArrayList<String> workloadAL = new ArrayList<String>();
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(workloadFile));
			String str = br.readLine();
			while (str != null)
			{
				workloadAL.add(str);
				str = br.readLine();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		Library.numAllJobs = workloadAL.size();
		int numNode = Network.size();
		int numPart = numNode / partSize;
		int numJobPerCtrl = workloadAL.size() / numPart;
		
		for (int i = 0; i < numNode; i++)
		{
			Node node = (Node)Network.get(i);
			PeerProtocol pp = (PeerProtocol)node.getProtocol(pid);
			pp.id = i;
			pp.ctrlId = i / partSize * partSize;
			if (i % partSize == 0)
			{
				pp.ctrlMaxProcTime = 0;
				pp.ctrlMaxFwdTime = 0;
				pp.kvsMaxProcTime = 0;
				pp.kvsMaxFwdTime = 0;
				pp.msgCount = 0;
				pp.hmData = new HashMap<Object, Object>();
				pp.numCDRegist = 0;
				pp.memList = new String[numPart];
				for (int j = 0; j < numPart; j++)
				{
					pp.memList[j] = "node-" + Integer.toString(j * partSize);
				}
				pp.res = new Resource();
				pp.numJobs = numJobPerCtrl;
				pp.numJobsStart = 0;
				pp.numJobsFin = 0;
				pp.workload = new ArrayList<String>();
				for (int j = 0; j < numJobPerCtrl; j++)
				{
					pp.workload.add(workloadAL.get(i / partSize * numJobPerCtrl + j));
				}
				pp.throughput = 0;
				pp.callbackHM = new HashMap<String, Integer>();
			}
			pp.cdMaxProcTime = 0;
			pp.cdMaxFwdTime = 0;
		}
	}
	
	public boolean execute()
	{
		initLibrary();
		initPeer();
		return false;
	}
}