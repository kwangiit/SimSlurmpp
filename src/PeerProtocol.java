/* The protocol of peer (both server and client), it implements all the 
 * behavior of the peer, including a compute daemon, a controller (zht client), 
 * a zht server. 
 */

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.edsim.EDSimulator;

import java.io.IOException;
import java.util.*;

public class PeerProtocol implements EDProtocol 
{
	private static final String PARA_TRANSPORT = "transport";
	private static final String PARA_PARTITIONSIZE = "partSize";
	private static final String PARA_SLEEPLENGTH = "sleepLength";
	private static final String PARA_CALLBACKINTERVAL = "callbackInterval";
	private static final String PARA_CALLBACKNUMTRY = "callbackNumTry";
	private static final String PARA_MAXNUMTRY = "maxNumTry";
	 
	public Parameters par;
	public int partSize;
	public long sleepLength;
	public long callbackInterval;
	public int callbackNumTry;
	public int maxNumTry;
	
	public String prefix;
	public int id;
	public int ctrlId;
	public long ctrlMaxProcTime;
	public long ctrlMaxFwdTime;
	public long kvsMaxProcTime;
	public long kvsMaxFwdTime;
	public long cdMaxProcTime;
	public long cdMaxFwdTime;
	public long msgCount;
	public HashMap<Object, Object> hmData;
	public int numCDRegist;
	public Resource res;
	public int jobStartIndex;
	public int numJobsStart;
	public int numJobsFin;
	public double throughput;
	
	public HashMap<String, Integer> callbackHM;
	/* initialization
	 * read the parameters from the configuration file
	 */
	public PeerProtocol(String prefix)
	{
		this.prefix = prefix;
		this.par = new Parameters();
		this.par.tid = Configuration.getPid(prefix + "." + PARA_TRANSPORT);
		this.partSize = Configuration.getInt(prefix + "." + PARA_PARTITIONSIZE);
		this.sleepLength = Configuration.getLong(prefix + "." + PARA_SLEEPLENGTH);
		this.callbackInterval = Configuration.getLong(prefix + "." + PARA_CALLBACKINTERVAL);
		this.callbackNumTry = Configuration.getInt(prefix + "." + PARA_CALLBACKNUMTRY);
		this.maxNumTry = Configuration.getInt(prefix + "." + PARA_MAXNUMTRY);
	}
	
	public long updateTime(long increment, long base)
	{
		if (CommonState.getTime() > base)
		{
			base = CommonState.getTime();
		}
		base += increment;
		return base;
	}
	
	public long timeCompOverride(long src, long dest)
	{
		if (src < dest)
		{
			src = dest;
		}
		return src;
	}
	
	/* calculate how much time to wait for a message */
	public long waitTimeCal(long endTime)
	{
		return endTime - CommonState.getTime();
	}
	
	/* hash to the correct server */
	public int hashServer(Object key)
	{
		int hashCode = Math.abs(key.hashCode());
		String server = Library.memList[hashCode % Library.memList.length];
		return Integer.parseInt(server.substring(5));
	}
	
	public void sendMsg(Message msg, long time)
	{
		byte[] msgByte = Library.serialize(msg);
		long endTime = time + Library.getCommOverhead(msgByte.length);
		EDSimulator.add(waitTimeCal(endTime), msg, Network.get(msg.destId), par.pid);
	}
	
	public void regist(long wait)
	{
		String nodeName = "node-" + Integer.toString(id);
		Message msg = new Message(id, ctrlId, "registration", nodeName);
		sendMsg(msg, wait + Library.sendOverhead);
	}

	public void kvsClientInteract(Pair pair)
	{
		ctrlMaxFwdTime = updateTime(Library.sendOverhead, ctrlMaxFwdTime);
		int destId = hashServer(pair.key);
		Message msg = new Message(id, destId, "kvs", pair);
		sendMsg(msg, ctrlMaxFwdTime);
	}
	
	public void printOutResult()
	{
		if (numJobsFin == Library.numJobsPerCtrl)
		{
			try
			{
				Library.bwThroughput.write("The throughput of controller " + id + " is:" + 
					(double)numJobsFin / (double)ctrlMaxFwdTime * 1E6 + "\r\n");
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		if (Library.numJobFinished == Library.numAllJobs)
		{
			try
			{
				Library.bwThroughput.write("The overall throughput is:" + (double)
						Library.numAllJobs / (double)ctrlMaxFwdTime * 1E6 + "\r\n");
				Library.bwThroughput.write("The number of insert message is:" + 
						Library.numInsertMsg + "\r\n");
				Library.bwThroughput.write("The number of lookup message is:" + 
						Library.numLookupMsg + "\r\n");
				Library.bwThroughput.write("The number of compare and swap message is:" + 
						Library.numCswapMsg + "\r\n");
				Library.bwThroughput.write("The number of callback message is:" + 
						Library.numCallbackMsg + "\r\n");
				Library.bwThroughput.write("The number of all ZHT message is:" + 
						(Library.numInsertMsg + Library.numLookupMsg + 
						 Library.numCswapMsg + Library.numCallbackMsg) + "\r\n");
				Library.bwThroughput.flush();
				Library.bwThroughput.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			Library.outputTaskDetail(Network.size() / partSize, partSize, Library.numJobsPerCtrl);
		}
	}
	
	public void procRegistEvent(Message registMsg)
	{
		numCDRegist++;
		ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
		res.numAvailNode++;
		res.nodeLL.add((String)registMsg.content);
		if (numCDRegist == partSize)
		{
			
			String key = "node-" + Integer.toString(id);
			Pair resPair = new Pair(key, res, null, null, "insert", "insert resource");
			kvsClientInteract(resPair);
		}
	}
	
	public KVSReturnObj procKVSEventAct(Pair pair)
	{
		KVSReturnObj kvsRetObj = new KVSReturnObj();
		kvsRetObj.key = pair.key;
		kvsRetObj.identifier = pair.identifier;
		kvsRetObj.type = pair.type;
		kvsRetObj.forWhat = pair.forWhat;
		if (pair.type.equals("insert"))
		{
			Library.numInsertMsg++;
			hmData.put(pair.key, pair.value);
			kvsRetObj.value = pair.value;
			kvsRetObj.result = true;
		}
		else if (pair.type.equals("lookup"))
		{
			Library.numLookupMsg++;
			kvsRetObj.value = hmData.get(pair.key);
			kvsRetObj.result = true;
		}
		else if (pair.type.equals("compare and swap"))
		{
			Library.numCswapMsg++;
			Resource cur = (Resource)hmData.get(pair.key);
			if (cur.comResource((Resource)pair.value) == 0)
			{
				hmData.put(pair.key, pair.attemptValue);
				kvsRetObj.result = true;
			}
			else
			{
				kvsRetObj.result = false;
			}
			kvsRetObj.value = cur;
		}
		else if (pair.type.equals("callback"))
		{
			Library.numCallbackMsg++;
			if (!callbackHM.containsKey(pair.key))
			{
				callbackHM.put((String)pair.key, 1);
			}
			else
			{
				int numTime = callbackHM.get((String)pair.key);
				callbackHM.put((String)pair.key, numTime + 1);
			}
			String value = (String)hmData.get(pair.key);
			if (value != null && value.equals("done"))
			{
				kvsRetObj.value = value;
				kvsRetObj.result = true;
			}
			else
			{
				kvsRetObj.result = false;
			}
		}
		return kvsRetObj;
	}

	public void procKVSEvent(Message msg)
	{
		Pair kvsPair = (Pair)msg.content;
		if (!kvsPair.forWhat.equals("recheck callback"))
		{
			kvsMaxFwdTime = updateTime(Library.recvOverhead, kvsMaxFwdTime);
			kvsMaxProcTime = timeCompOverride(kvsMaxProcTime, kvsMaxFwdTime);
			kvsMaxProcTime = updateTime(Library.kvsProcTime, kvsMaxProcTime);
		}
		KVSReturnObj kvsRetObj = procKVSEventAct(kvsPair);
		boolean needSend = true;
		if (kvsRetObj.type == "callback" && !kvsRetObj.result)
		{
			if (callbackHM.get((String)kvsRetObj.key) > callbackNumTry)
			{
				callbackHM.remove((String)kvsRetObj.key);
				needSend = true;
			}
			else
			{
				Pair cbReCheckPair = new Pair(kvsPair.key, kvsPair.value, 
					kvsPair.attemptValue, kvsPair.identifier, kvsPair.type, "recheck callback");
				Message recheckMsg = new Message(msg.sourceId, id, "kvs", cbReCheckPair);
				EDSimulator.add(callbackInterval, recheckMsg, Network.get(id), par.pid);
				needSend = false;
			}
		}
		if (needSend)
		{
			kvsMaxFwdTime = timeCompOverride(kvsMaxFwdTime, kvsMaxProcTime);
			kvsMaxFwdTime = updateTime(Library.sendOverhead, kvsMaxFwdTime);
			Message retMsg = new Message(id, msg.sourceId, "kvs return", kvsRetObj);
			sendMsg(retMsg, kvsMaxFwdTime);
		}
	}
	
	public String[] parseJob(String jobDesc)
	{
		String[] jobArray = jobDesc.split(" ");
		return jobArray;
	}
	
	public Job createJob(String[] jobArray)
	{
		Job job = new Job();
		job.client = "node-" + Integer.toString(id);
		job.jobId = job.client + " " + Integer.toString(numJobsStart++);
		job.prefix = jobArray[0];
		job.numNodeRequired = Integer.parseInt(jobArray[1].substring(2));
		job.numCoresRequiredPerNode = -1;
		job.numNodeTransmitted = 0;
		job.numNodeReturnRes = 0;
		job.dir = jobArray[2];
		job.cmd = jobArray[3];
		job.argv = "";
		for (int i = 4; i < jobArray.length; i++)
		{
			job.argv += jobArray[i];
			if (i != jobArray.length - 1)
			{
				job.argv += " ";
			}
		}
		job.nodelist = new LinkedList<String>();
		job.ctrls = new LinkedList<String>();
		job.ctrlNodelist = new LinkedList<Resource>();
		job.resBackup = new Resource();
		
		job.startTime = CommonState.getTime();
		job.submitTime = 0;
		job.exeTime = 0;
		job.finTime = 0;
		job.backTime = 0;
		
		Library.jobMetaData.put(job.jobId, job);
		return job;
	}
	
	public void executeJob(String jobId)
	{
		if (jobId.isEmpty())
		{
			ctrlMaxProcTime = updateTime(Library.jobProcTime, ctrlMaxProcTime);
			String[] jobArray = parseJob(Library.workload.get(numJobsStart + jobStartIndex));
			Job job = createJob(jobArray);
			jobId = job.jobId;
		}
		ctrlMaxFwdTime = timeCompOverride(ctrlMaxFwdTime, ctrlMaxProcTime);
		String key = "node-" + Integer.toString(id);
		Pair resPair = new Pair(key, null, null, jobId, "lookup", "lookup resource");
		kvsClientInteract(resPair);
	}
	
	public void splitResource(Resource initRes, Resource firstRes, Resource lastRes, int num)
	{
		firstRes.numAvailNode = num;
		for (int i = 0; i < num; i++)
		{
			firstRes.nodeLL.add(initRes.nodeLL.get(i));
		}
		lastRes.numAvailNode = initRes.numAvailNode - num;
		for (int i = 0; i < lastRes.numAvailNode; i++)
		{
			lastRes.nodeLL.add(initRes.nodeLL.get(i + num));
		}
	}
	
	public void randSelect(String identifier)
	{
		String anoCtrlId = Library.memList[CommonState.r.nextInt(Library.memList.length)];
		Pair resPair = new Pair(anoCtrlId, null, null, identifier, "lookup", "lookup resource");
		kvsClientInteract(resPair);
	}
	
	/* lookup for releasing resources 
	 * i = 0 means releasing resources after a job cannot be satisfied
	 * i = 1 means releasing resources after finishing a job
	 * */
	public void releaseResLookup(KVSReturnObj kvsRetObj, int i)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		if (job.ctrls.size() > 0)
		{
			String firstCtrl = job.ctrls.getFirst();
			Pair pair = new Pair(firstCtrl, null, null, job.jobId, 
								"lookup", "release resource" + Integer.toString(i));
			kvsClientInteract(pair);
		}
		else 
		{
			
			if (job.nodelist.size() > 0)
			{
				job.nodelist.clear();
			}
			if (i == 0)
			{
				Message msg = new Message(id, id, "reallocation", job.jobId);
				EDSimulator.add(sleepLength, msg, Network.get(id), par.pid);
			}
			if (i == 1)
			{
				int jobClientId = Integer.parseInt(job.jobId.split(" ")[0].substring(5));
				if (id == jobClientId)
				{
					job.backTime = ctrlMaxFwdTime;
					numJobsFin++;
					Library.numJobFinished++;
					printOutResult();
				}
				else
				{
					Pair pair = new Pair(kvsRetObj.identifier + "Fin", "done", 
							null, kvsRetObj.identifier, "insert", "notify job fin");
					kvsClientInteract(pair);
				}
			}
		}
	}
	
	public void allocateRes(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		int numMoreNodeRequired = job.numNodeRequired - job.nodelist.size();
		job.ctrlBackup = null; job.resBackup.numAvailNode = 0; job.resBackup.nodeLL.clear();
		Resource seenRes = (Resource)kvsRetObj.value;
		int numNodeAllocated = seenRes.numAvailNode >= numMoreNodeRequired ? 
				numMoreNodeRequired : seenRes.numAvailNode;
		if (numNodeAllocated > 0)
		{
			Resource attemptRes = new Resource();
			splitResource(seenRes, job.resBackup, attemptRes, numNodeAllocated);
			job.ctrlBackup = (String)kvsRetObj.key;
			Pair cswapPair = new Pair(kvsRetObj.key, kvsRetObj.value, attemptRes, job.jobId,
										"compare and swap", "allocate resource");
			kvsClientInteract(cswapPair);
		}
		else	// there are no more available nodes for the selected controller
		{
			job.numTry++;
			if (job.numTry < maxNumTry)
			{
				Message msg = new Message(id, id, "retry", job.jobId);
				EDSimulator.add(sleepLength, msg, Network.get(id), par.pid);
				//randSelect(job.jobId);
			}
			else
			{
				job.numTry = 0;
				releaseResLookup(kvsRetObj, 0);
			}
		}
	}
	
	public void mergeResource(Resource firstRes, Resource secRes)
	{
		firstRes.numAvailNode += secRes.numAvailNode;
		for (int i = 0; i < secRes.numAvailNode; i++)
		{
			firstRes.nodeLL.add(secRes.nodeLL.get(i));
		}
	}
	
	public void releaseResCswap(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		Resource seenRes = (Resource)kvsRetObj.value;
		Resource attemptRes = new Resource();
		mergeResource(attemptRes, seenRes);
		mergeResource(attemptRes, job.ctrlNodelist.getFirst());
		Pair pair = new Pair(kvsRetObj.key, seenRes, attemptRes, 
				kvsRetObj.identifier, "compare and swap", kvsRetObj.forWhat);
		kvsClientInteract(pair);
	}
	
	/* insert (jobid, origin controller id) */
	public void insertJobOriginCtrl(Job job)
	{
		Pair jobOriginCtrlPair = new Pair(job.jobId, "node-" + Integer.toString(id), 
										null, job.jobId, "insert", "job origin ctrl");
		kvsClientInteract(jobOriginCtrlPair);
	}
	
	public void cswapAllocResSuc(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		int pos = job.ctrls.indexOf(job.ctrlBackup);
		if (pos == -1)
		{
			job.ctrls.add(job.ctrlBackup);
			Resource tmpRes = new Resource();
			mergeResource(tmpRes, job.resBackup);
			job.ctrlNodelist.add(tmpRes);
		}
		else
		{
			Resource res = job.ctrlNodelist.get(pos);
			mergeResource(res, job.resBackup);
		}
		for (int i = 0; i < job.resBackup.numAvailNode; i++)
		{
			job.nodelist.add(job.resBackup.nodeLL.get(i));
		}
		if (job.nodelist.size() < job.numNodeRequired)
		{
			randSelect(job.jobId);
		}
		else
		{
			if (numJobsStart < Library.numJobsPerCtrl)
			{
				executeJob(new String());	//start to handle the next job
			}
			insertJobOriginCtrl(job);
		}
	}
	
	public void insertSelfHereMsg(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		int flag = 0, selfInv = 0;
		for (; flag < job.ctrls.size(); flag++)
		{
			int target = Integer.parseInt(job.ctrls.get(flag).substring(5));
			if (target < id)
			{
				break;
			}
			else
			{
				if (target == id)
				{
					selfInv = 1;
				}
			}
		}
		if (flag == job.ctrls.size() && selfInv == 1)
		{
			String key = job.jobId + "node-" + Integer.toString(id);
			Pair pair = new Pair(key, "I am here", null, job.jobId, "insert", "mark self");
			kvsClientInteract(pair);
		}
		else
		{
			insertJobCtrls(kvsRetObj);
		}
	}
	
	/* insert (jobid + origin controller id, involved controller list) */
	public void insertJobCtrls(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		String key = job.jobId + "node-" + Integer.toString(id) + "ctrls";
		Pair jobCtrlsPair = new Pair(key, job.ctrls, null, job.jobId, "insert", "job ctrls");
		kvsClientInteract(jobCtrlsPair);
	}
	
	/* launch jobs in a tree topology rooted at rank 0 */
	public void transmitJob(boolean origin, Job job, boolean left)
	{
		int pos = job.nodelist.indexOf("node-" + Integer.toString(id));
		boolean keepTransmit = false;
		Message jobTransmitMsg = new Message();
		jobTransmitMsg.sourceId = id;
		jobTransmitMsg.msgType = "transmit job";
		jobTransmitMsg.content = job;
		if (origin)
		{
			jobTransmitMsg.destId = Integer.parseInt(job.nodelist.get(0).substring(5));
			keepTransmit = true;
		}
		else
		{
			int next = 2 * pos + 1;
			if (!left)
			{
				next++;
			}
			if (next < job.nodelist.size())
			{
				jobTransmitMsg.destId = Integer.parseInt(job.nodelist.get(next).substring(5));
				keepTransmit = true;
			}
			else
			{
				keepTransmit = false;
			}
		}
		if (keepTransmit)
		{
			long time = 0;
			if (origin)
			{
				ctrlMaxFwdTime = updateTime(Library.sendOverhead, ctrlMaxFwdTime);
				time = ctrlMaxFwdTime;
			}
			else
			{
				cdMaxFwdTime = updateTime(Library.sendOverhead, cdMaxFwdTime);
				time = cdMaxFwdTime;
			}
			sendMsg(jobTransmitMsg, time);
		}
	}
	
	public void launchJob(Job job)
	{
		Collections.sort(job.nodelist, new NodelistComp());
		job.submitTime = CommonState.getTime();
		Library.jobMetaData.put(job.jobId, job);
		transmitJob(true, job, false);
	}
	
	/* insert the resource used of each controller for a job */
	public void insertJobCtrlNodelist(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		String key = job.jobId + "node-" + Integer.toString(id);
		Resource valueRes;
		if (kvsRetObj.forWhat.equals("job ctrls"))	// if this is the fisrt controller
		{
			key += job.ctrls.get(0);
			valueRes = job.ctrlNodelist.get(0);
			Pair pair = new Pair(key, valueRes, null, job.jobId, "insert", "job ctrl nodelist");
			kvsClientInteract(pair);
		}
		else
		{
			/* find the current controller, and insert the next one */
			Resource lastRes = (Resource)kvsRetObj.value;
			int pos = job.ctrlNodelist.indexOf(lastRes);
			if (pos < job.ctrlNodelist.size() - 1 && pos >=0 )
			{
				pos++;
				key += job.ctrls.get(pos);
				valueRes = job.ctrlNodelist.get(pos);
				Pair pair = new Pair(key, valueRes, null, job.jobId, 
									"insert", "job ctrl nodelist");
				kvsClientInteract(pair);
			}
			else	// if this is the last controller, then can launch the job
			{
				launchJob(job);
			}
		}
	}
	
	public void procKVSRetEvent(Message msg)
	{
		ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
		KVSReturnObj kvsRetObj = (KVSReturnObj)msg.content;
		if (!kvsRetObj.result)
		{
			if (kvsRetObj.type.equals("compare and swap"))
			{
				if (kvsRetObj.forWhat.equals("allocate resource"))
				{
					allocateRes(kvsRetObj);
				}
				if (kvsRetObj.forWhat.startsWith("release resource"))
				{
					releaseResCswap(kvsRetObj);
				}
			}
			else
			{
				Pair pair = new Pair(kvsRetObj.key, kvsRetObj.value, null, 
						kvsRetObj.identifier, kvsRetObj.type, kvsRetObj.forWhat);
				kvsClientInteract(pair);
			}
		}
		else
		{
			if (kvsRetObj.type.equals("compare and swap"))
			{
				if (kvsRetObj.forWhat.equals("allocate resource"))
				{
					cswapAllocResSuc(kvsRetObj);
				}
				if (kvsRetObj.forWhat.startsWith("release resource"))
				{
					Job job = Library.jobMetaData.get(kvsRetObj.identifier);
					job.ctrls.removeFirst();
					job.ctrlNodelist.removeFirst();
					char releaseType = kvsRetObj.forWhat.charAt(kvsRetObj.forWhat.length() - 1);
					if (releaseType == '0')
					{
						releaseResLookup(kvsRetObj, 0);
					}
					else
					{
						releaseResLookup(kvsRetObj, 1);
					}
				}
			}
			else if (kvsRetObj.type.equals("insert"))
			{
				if (kvsRetObj.forWhat.equals("insert resource"))
				{
					executeJob(new String());
				}
				if (kvsRetObj.forWhat.equals("job origin ctrl"))
				{
					insertSelfHereMsg(kvsRetObj);
					//insertJobCtrls(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("mark self"))
				{
					insertJobCtrls(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job ctrls"))	// This is where I left last time
				{
					insertJobCtrlNodelist(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job ctrl nodelist"))
				{
					insertJobCtrlNodelist(kvsRetObj);
				}
				//if (kvsRetObj.forWhat.equals("notify job fin"))
				//{
				//	releaseResLookup(kvsRetObj, 1);
				//}
			}
			else if (kvsRetObj.type.equals("lookup"))
			{
				if (kvsRetObj.forWhat.equals("lookup resource"))
				{
					allocateRes(kvsRetObj);
				}
				if (kvsRetObj.forWhat.startsWith("release resource"))
				{
					releaseResCswap(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("mark self"))
				{
					markSelfRet(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job origin ctrl"))
				{
					jobOriginCtrlMsgProc(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job ctrls"))
				{
					lookupJobCtrlNodelist(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job ctrl nodelist"))
				{
					lookupJobCtrlNodelist(kvsRetObj);
				}
			}
			else if (kvsRetObj.type.equals("callback"))
			{
				callbackSuc(kvsRetObj);
			}
		}
	}
	
	public void transmitJobMsgProc(Message msg)
	{
		cdMaxFwdTime = updateTime(Library.recvOverhead, cdMaxFwdTime);
		Job job = Library.jobMetaData.get(((Job)msg.content).jobId);
		job.numNodeTransmitted++;
		Message ackMsg = new Message(id, msg.sourceId, "transmit job ack", job);
		cdMaxFwdTime = updateTime(Library.sendOverhead, cdMaxFwdTime);
		sendMsg(ackMsg, cdMaxFwdTime);
		transmitJob(false, job, true);
	}
	
	public void transmitJobAckMsgProc(Message msg)
	{
		Job job = Library.jobMetaData.get(((Job)msg.content).jobId);
		job.numNodeTransmitted++;
		int srcPos = job.nodelist.indexOf("node-" + Integer.toString(msg.sourceId));
		int curPos = job.nodelist.indexOf("node-" + Integer.toString(id));
		long time = 0;
		if (curPos >= 0 && srcPos > 0)
		{
			cdMaxFwdTime = updateTime(Library.recvOverhead, cdMaxFwdTime);
			time = cdMaxFwdTime;
		}
		else
		{
			ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
			time = ctrlMaxFwdTime;
		}
		if (job.numNodeTransmitted == job.numNodeRequired * 2)
		{
			for (int i = 0; i < job.nodelist.size(); i++)
			{
				Message execJobMsg = new Message(-1, Integer.parseInt(
						job.nodelist.get(i).substring(5)), "execute job", job);
				sendMsg(execJobMsg, time);
			}
			//int firstNodeId = Integer.parseInt(job.nodelist.getFirst().substring(5));
			//Node node = Network.get(firstNodeId);
			//PeerProtocol pp = (PeerProtocol)node.getProtocol(par.pid);
			String jobClient = job.jobId.split(" ")[0];
			int jobClientId = Integer.parseInt(jobClient.substring(5));
			//if (pp.ctrlId != jobClientId)
			//{
			//	Pair pair = new Pair(job.jobId + "Fin", null, null, 
			//			job.jobId, "callback", "wait for notification");
			//	int destId = hashServer(pair.key);
			//	Message callbackMsg = new Message(jobClientId, destId, "kvs", pair);
			//	sendMsg(callbackMsg, time);
			//}
			Pair pair = new Pair(job.jobId + jobClient, null, null, 
								job.jobId, "lookup", "mark self");
			int destId = hashServer(pair.key);
			Message lookupMsg = new Message(jobClientId, destId, "kvs", pair);
			sendMsg(lookupMsg, time);
		}
		else
		{
			if (curPos >= 0 && srcPos > 0)
			{
				if (srcPos == curPos * 2 + 1)
				{
					transmitJob(false, job, false);
				}
			}
		}
	}
	
	public void sendJobDone(Job job)
	{
		if (job.numNodeReturnRes == job.numNodeRequired - 1)
		{
			Message jobDoneMsg = new Message(id, ctrlId, "job done", job);
			cdMaxFwdTime = updateTime(Library.sendOverhead, cdMaxFwdTime);
			sendMsg(jobDoneMsg, cdMaxFwdTime);
			job.finTime = CommonState.getTime();
			Library.jobMetaData.put(job.jobId, job);
		}
	}
	
	public void execJobMsgProc(Message msg)
	{
		Job job = Library.jobMetaData.get(((Job)msg.content).jobId);
		if (job.exeTime == 0)
		{
			job.exeTime = CommonState.getTime();
		}
		long startTime = System.nanoTime();
		/*final Runtime rt = Runtime.getRuntime();
		try 
		{
			rt.exec(job.dir + job.cmd + " " + job.args);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}*/
		try
		{
			Thread.sleep(Long.parseLong(job.argv) * 1000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		long endTime = System.nanoTime();
		long procTime = (endTime - startTime) / 1000;
		cdMaxProcTime = updateTime(procTime, cdMaxProcTime);
		cdMaxFwdTime = timeCompOverride(cdMaxFwdTime, cdMaxProcTime);
		int pos = job.nodelist.indexOf("node-" + Integer.toString(id));
		if (pos != 0)
		{
			Message jobFinMsg = new Message(id, Integer.parseInt(
					job.nodelist.get(0).substring(5)), "one job finish", job);
			cdMaxFwdTime = updateTime(Library.sendOverhead, cdMaxFwdTime);
			sendMsg(jobFinMsg, cdMaxFwdTime);
		}
		else
		{
			sendJobDone(job);
		}
	}
	
	public void oneJobFinMsgProc(Message msg)
	{
		cdMaxFwdTime = updateTime(Library.recvOverhead, cdMaxFwdTime);
		Job job = Library.jobMetaData.get(((Job)msg.content).jobId);
		job.numNodeReturnRes++;
		sendJobDone(job);
	}
	
	public void jobDoneMsgProc(Message msg)
	{
		ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
		Job job = (Job)msg.content;
		Pair pair = new Pair(job.jobId, null, null, job.jobId, "lookup", "job origin ctrl");
		kvsClientInteract(pair);
	}
	
	public void markSelfRet(KVSReturnObj kvsRetObj)
	{
		String selfHere = (String)kvsRetObj.value;
		if (selfHere == null || !selfHere.equals("I am here"))
		{
			Pair pair = new Pair(kvsRetObj.identifier + "Fin", null, null, kvsRetObj.identifier, 
								  "callback", "wait for notification");
			kvsClientInteract(pair);
		}
	}
	
	public void jobOriginCtrlMsgProc(KVSReturnObj kvsRetObj)
	{
		String originCtrl = (String)kvsRetObj.value;
		Pair pair = new Pair(kvsRetObj.identifier + originCtrl + "ctrls", null, null, 
				              kvsRetObj.identifier, "lookup", "job ctrls");
		kvsClientInteract(pair);
		//if (originCtrl.equals("node-" + Integer.toString(id)))
		//{
		//	Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		//	job.backTime = ctrlMaxFwdTime;
			//releaseResLookup(kvsRetObj, 1);
		//}
		/*else
		{
			Pair pair = new Pair(kvsRetObj.identifier + "Fin", "done", 
					null, kvsRetObj.identifier, "insert", "notify job fin");
			kvsClientInteract(pair);
		}*/
		//releaseResLookup(kvsRetObj,1);
	}
	
	public void lookupJobCtrlNodelist(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		String key = job.jobId + job.jobId.split(" ")[0];
		if (kvsRetObj.forWhat.equals("job ctrls"))	// if this is the fisrt controller
		{
			key += job.ctrls.get(0);
			Pair pair = new Pair(key, null, null, job.jobId, "lookup", "job ctrl nodelist");
			kvsClientInteract(pair);
		}
		else
		{
			/* find the current controller, and insert the next one */
			Resource lastRes = (Resource)kvsRetObj.value;
			int pos = job.ctrlNodelist.indexOf(lastRes);
			if (pos < job.ctrlNodelist.size() - 1 && pos >=0 )
			{
				pos++;
				key += job.ctrls.get(pos);
				Pair pair = new Pair(key, null, null, job.jobId, 
									"lookup", "job ctrl nodelist");
				kvsClientInteract(pair);
			}
			else	// if this is the last controller, then can launch the job
			{
				releaseResLookup(kvsRetObj,1);
			}
		}		
	}
	
	public void callbackSuc(KVSReturnObj kvsRetObj)
	{
		numJobsFin++;
		Library.numJobFinished++;
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		job.backTime = ctrlMaxFwdTime;
		printOutResult();
	}

	public void processEvent(Node node, int pid, Object event)
	{
		Message msg = (Message)event;
		boolean increment = false;
		if (!msg.msgType.equals("reallocation") &&
			!msg.msgType.equals("retry") &&
		    !msg.msgType.equals("execute job"))
		{
			if (msg.msgType.equals("kvs"))
			{
				Pair pair = (Pair)msg.content;
				if (!pair.forWhat.equals("recheck callback"))
				{
					increment = true;
				}
			}
			else
			{
				increment = true;
			}
		}
		if (increment)
		{
			msgCount++;
			Library.numAllMsg++;
		}
		if (msg.msgType.equals("registration"))
		{
			procRegistEvent(msg);
		}
		else if (msg.msgType.equals("kvs"))
		{
			procKVSEvent(msg);
		}
		else if (msg.msgType.equals("kvs return"))
		{
			procKVSRetEvent(msg);
		}
		else if (msg.msgType.equals("retry"))
		{
			randSelect((String)msg.content);
		}
		else if (msg.msgType.equals("reallocation"))
		{
			executeJob((String)msg.content);
		}
		else if (msg.msgType.equals("transmit job"))
		{
			transmitJobMsgProc(msg);
		}
		else if (msg.msgType.equals("transmit job ack"))
		{
			transmitJobAckMsgProc(msg);
		}
		else if (msg.msgType.equals("execute job"))
		{
			execJobMsgProc(msg);
		}
		else if (msg.msgType.equals("one job finish"))
		{
			oneJobFinMsgProc(msg);
		}
		else if (msg.msgType.equals("job done"))
		{
			jobDoneMsgProc(msg);
		}
		else
		{
			System.out.println("Unknown message type:" + 
							   msg.msgType + ", please check!");
		}
	}
	
	public Object clone()
	{
		PeerProtocol pp = new PeerProtocol(this.prefix);
		return pp;
	}
}