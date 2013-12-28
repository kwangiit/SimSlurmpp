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
	public int[] memList;
	public Resource res;
	public int numJobs;
	public int numJobsStart;
	public int numJobsFin;
	public ArrayList<String> workload;
	public double throughput;
	
	public HashMap<String, Integer> callbackTime;
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
	
	public void kvsClientInteract(Pair pair)
	{
		ctrlMaxFwdTime = updateTime(Library.sendOverhead, ctrlMaxFwdTime);
		int destId = hashServer(pair.key);
		Message msg = new Message(id, destId, "kvs", pair);
		sendMsg(msg, ctrlMaxFwdTime);
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
		job.jobId = job.client + Integer.toString(numJobsStart++);
		job.prefix = jobArray[0];
		job.numNodeRequired = Integer.parseInt(jobArray[1].substring(2));
		job.numCoresRequiredPerNode = -1;
		job.numNodeTransmitted = 0;
		job.numNodeReturnRes = 0;
		job.dir = jobArray[2];
		job.cmd = jobArray[3];
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
	
	public void randSelect(String identifier)
	{
		int anoCtrlId = memList[CommonState.r.nextInt(memList.length)];
		String key = Integer.toString(anoCtrlId);
		Pair resPair = new Pair(key, null, null, identifier, "lookup", "lookup resource");
		kvsClientInteract(resPair);
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
		else
		{
			job.numTry++;
			if (job.numTry < maxNumTry)
			{
				randSelect(job.jobId);
			}
			else
			{
				releaseResLookup(kvsRetObj, 0);
			}
		}
	}
	
	public void releaseResLookup(KVSReturnObj kvsRetObj, int i)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		if (job.ctrls.size() > 0)
		{
			String firstCtrl = job.ctrls.getFirst();
			Pair pair = new Pair(firstCtrl.substring(5), null, null, job.jobId, 
								"lookup", "release resource" + Integer.toString(i));
			kvsClientInteract(pair);
		}
		else if (job.nodelist.size() > 0)
		{
			job.nodelist.clear();
		}
		if (i == 0)
		{
			Message msg = new Message(id, id, "reallocation", job.jobId);
			EDSimulator.add(sleepLength, msg, Network.get(id), par.pid);
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
	
	public void splitResource(Resource initRes, Resource firstRes, Resource lastRes, int num)
	{
		firstRes.numAvailNode = num;
		firstRes.nodeLL = (LinkedList<String>)(initRes.nodeLL.subList(0, num));
		lastRes.numAvailNode = initRes.numAvailNode - num;
		lastRes.nodeLL = (LinkedList<String>)(initRes.nodeLL.subList(num, initRes.numAvailNode));
	}
	
	public void mergeResource(Resource firstRes, Resource secRes)
	{
		firstRes.numAvailNode += secRes.numAvailNode;
		for (int i = 0; i < secRes.numAvailNode; i++)
		{
			firstRes.nodeLL.add(secRes.nodeLL.get(i));
		}
	}
	
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
	
	public void transmitJobMsgProc(Message msg)
	{
		cdMaxFwdTime = updateTime(Library.recvOverhead, cdMaxFwdTime);
		Job job = (Job)msg.content;
		job.numNodeTransmitted++;
		Library.jobMetaData.put(job.jobId, job);
		Message ackMsg = new Message(id, msg.sourceId, "transmit job ack", job);
		cdMaxFwdTime = updateTime(Library.sendOverhead, cdMaxFwdTime);
		sendMsg(ackMsg, cdMaxFwdTime);
		transmitJob(false, job, true);
	}
	
	public void transmitJobAckMsgProc(Message msg)
	{
		Job job = (Job)msg.content;
		job.numNodeTransmitted++;
		Library.jobMetaData.put(job.jobId, job);
		int srcPos = job.nodelist.indexOf("node-" + Integer.toString(msg.sourceId));
		int curPos = job.nodelist.indexOf("node-" + Integer.toString(id));
		if (curPos >= 0 && srcPos > 0)
		{
			cdMaxFwdTime = updateTime(Library.recvOverhead, cdMaxFwdTime);
		}
		else
		{
			ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
		}
		if (job.numNodeTransmitted == job.numNodeRequired * 2)
		{
			Message execJobMsg = new Message(-1, -1, "execute job", job);
			for (int i = 0; i < job.nodelist.size(); i++)
			{
				sendMsg(execJobMsg, CommonState.getTime());
			}
		}
		else
		{
			if (curPos >= 0 && srcPos > 0)
			{
				if (curPos == srcPos * 2 + 1)
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
			cdMaxFwdTime = timeCompOverride(cdMaxFwdTime, cdMaxProcTime);
			cdMaxFwdTime = updateTime(Library.sendOverhead, cdMaxFwdTime);
			sendMsg(jobDoneMsg, cdMaxFwdTime);
			job.finTime = CommonState.getTime();
			Library.jobMetaData.put(job.jobId, job);
		}
	}
	
	public void execJobMsgProc(Message msg)
	{
		Job job = (Job)msg.content;
		job.exeTime = CommonState.getTime();
		Library.jobMetaData.put(job.jobId, job);
		long startTime = System.nanoTime();
		final Runtime rt = Runtime.getRuntime();
		try 
		{
			rt.exec(job.dir + job.cmd);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		long endTime = System.nanoTime();
		long procTime = (endTime - startTime) / 1000;
		cdMaxProcTime = updateTime(procTime, cdMaxProcTime);
		int pos = job.nodelist.indexOf("node-" + Integer.toString(id));
		if (pos != 0)
		{
			Message jobFinMsg = new Message(id, Integer.parseInt(
					job.nodelist.get(0).substring(5)), "one job finish", job);
			cdMaxFwdTime = timeCompOverride(cdMaxFwdTime, cdMaxProcTime);
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
	
	public void jobOriginCtrlMsgProc(KVSReturnObj kvsRetObj)
	{
		String originCtrl = (String)kvsRetObj.value;
		if (originCtrl.equals("node-" + Integer.toString(id)))
		{
			numJobsFin++;
			if (numJobsFin == workload.size())
			{
				System.out.println("The throughput is:" + (double)numJobsFin 
						/ (double)CommonState.getTime());
			}
			Library.numJobFinished++;
			Job job = Library.jobMetaData.get(kvsRetObj.identifier);
			job.backTime = CommonState.getTime();
			releaseResLookup(kvsRetObj, 1);
		}
		else
		{
			Pair pair = new Pair(kvsRetObj.identifier + "fin", "done", 
					null, kvsRetObj.identifier, "insert", "notify job fin");
			kvsClientInteract(pair);
		}
	}
	
	public void callbackSuc(KVSReturnObj kvsRetObj)
	{
		numJobsFin++;
		if (numJobsFin == workload.size())
		{
			System.out.println("The throughput is:" + (double)numJobsFin 
					/ (double)CommonState.getTime());
		}
		Library.numJobFinished++;
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		job.backTime = CommonState.getTime();
	}
	
	public void launchJob(Job job)
	{
		Collections.sort(job.nodelist, new NodelistComp());
		job.submitTime = CommonState.getTime();
		transmitJob(true, job, false);
		int firstNodeId = Integer.parseInt(job.nodelist.getFirst().substring(5));
		Node node = Network.get(firstNodeId);
		PeerProtocol pp = (PeerProtocol)node.getProtocol(par.pid);
		if (pp.ctrlId != id)
		{
			Pair pair = new Pair(job.jobId + "Fin", null, null, 
					job.jobId, "callback", "wait for notification");
			kvsClientInteract(pair);
		}
	}
	
	public void insertJobOriginCtrl(Job job)
	{
		Pair jobOriginCtrlPair = new Pair(job.jobId, "node-" + Integer.toString(id), 
										null, job.jobId, "insert", "job origin ctrl");
		kvsClientInteract(jobOriginCtrlPair);
	}
	
	public void insertJobCtrls(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		String key = job.jobId + "node-" + Integer.toString(id);
		Pair jobCtrlsPair = new Pair(key, job.ctrls, null, job.jobId, "insert", "job ctrls");
		kvsClientInteract(jobCtrlsPair);
	}
	
	public void insertJobCtrlNodelist(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		String key = job.jobId + "node-" + Integer.toString(id);
		Resource valueRes;
		if (kvsRetObj.forWhat.equals("job ctrls"))
		{
			key += job.ctrls.get(0);
			valueRes = job.ctrlNodelist.get(0);
			Pair pair = new Pair(key, valueRes, null, job.jobId, "insert", "job ctrl nodelist");
			kvsClientInteract(pair);
		}
		else
		{
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
			else
			{
				launchJob(job);
			}
		}
	}
	
	public void cswapAllocResSuc(KVSReturnObj kvsRetObj)
	{
		Job job = Library.jobMetaData.get(kvsRetObj.identifier);
		int pos = job.ctrls.indexOf(job.ctrlBackup);
		if (pos == -1)
		{
			job.ctrls.add(job.ctrlBackup);
			job.ctrlNodelist.add(job.resBackup);
		}
		else
		{
			Resource res = job.ctrlNodelist.get(pos);
			mergeResource(res, job.resBackup);
			res.numAvailNode += job.resBackup.numAvailNode;
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
			executeJob(new String());
			insertJobOriginCtrl(job);
		}
	}
	
	public void executeJob(String jobId)
	{
		if (jobId.isEmpty())
		{
			ctrlMaxProcTime = updateTime(Library.jobProcTime, ctrlMaxProcTime);
			String[] jobArray = parseJob(workload.get(numJobsStart));
			Job job = createJob(jobArray);
			jobId = job.jobId;
		}
		ctrlMaxFwdTime = timeCompOverride(ctrlMaxFwdTime, ctrlMaxProcTime);
		String key = Integer.toString(id);
		Pair resPair = new Pair(key, null, null, jobId, "lookup", "lookup resource");
		kvsClientInteract(resPair);
	}
	
	public void procRegistEvent(Message registMsg)
	{
		numCDRegist++;
		ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
		res.numAvailNode++;
		res.nodeLL.add(Integer.toString(registMsg.sourceId));
		if (numCDRegist == partSize)
		{
			String key = Integer.toString(id);
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
			hmData.put(pair.key, pair.value);
			kvsRetObj.value = pair.value;
			kvsRetObj.result = true;
		}
		else if (pair.type.equals("lookup"))
		{
			kvsRetObj.value = hmData.get(pair.key);
			kvsRetObj.result = true;
		}
		else if (pair.type.equals("compare_and_swap"))
		{
			Object cur = hmData.get(pair.key);
			if (cur.equals(pair.value))
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
			if (!callbackTime.containsKey(pair.key))
			{
				callbackTime.put((String)pair.key, 1);
			}
			else
			{
				int numTime = callbackTime.get((String)pair.key);
				callbackTime.put((String)pair.key, numTime + 1);
			}
			String value = (String)hmData.get(pair.key);
			if (value.equals("done"))
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
	
	public void sendMsg(Message msg, long time)
	{
		byte[] msgByte = Library.serialize(msg);
		long endTime = time + Library.getCommOverhead(msgByte.length);
		EDSimulator.add(waitTimeCal(endTime), msg, Network.get(msg.destId), par.pid);
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
			if (callbackTime.get((String)kvsRetObj.key) > callbackNumTry)
			{
				callbackTime.remove((String)kvsRetObj.key);
				needSend = true;
			}
			else
			{
				Pair cbReCheckPair = new Pair(kvsPair.key, kvsPair.value, 
					kvsPair.attemptValue, kvsPair.identifier, kvsPair.type, "recheck callback");
				Message recheckMsg = new Message(id, id, "kvs", cbReCheckPair);
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
	
	public void procKVSRetEvent(Message msg)
	{
		ctrlMaxFwdTime = updateTime(Library.recvOverhead, ctrlMaxFwdTime);
		KVSReturnObj kvsRetObj = (KVSReturnObj)msg.content;
		if (!kvsRetObj.result)
		{
			if (kvsRetObj.type.equals("compare_and_swap"))
			{
				if (kvsRetObj.forWhat.equals("allocate resource"))
				{
					allocateRes(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("release resource"))
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
			if (kvsRetObj.type.equals("compare_and_swap"))
			{
				if (kvsRetObj.forWhat.equals("allocate resource"))
				{
					cswapAllocResSuc(kvsRetObj);
				}
				if (kvsRetObj.forWhat.startsWith("release resource"))//kvsRetObj.forWhat.equals("release resource"))
				{
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
					insertJobCtrls(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job ctrls"))
				{
					insertJobCtrlNodelist(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("job ctrl nodelist"))
				{
					insertJobCtrlNodelist(kvsRetObj);
				}
				if (kvsRetObj.forWhat.equals("notify job fin"))
				{
					releaseResLookup(kvsRetObj, 1);
				}
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
				if (kvsRetObj.forWhat.equals("job origin ctrl"))
				{
					jobOriginCtrlMsgProc(kvsRetObj);
				}
			}
			else if (kvsRetObj.type.equals("callback"))
			{
				callbackSuc(kvsRetObj);
			}
		}
	}
	
	public void processEvent(Node node, int pid, Object event)
	{
		Message msg = (Message)event;
		msgCount++;
		Library.numAllMsg++;
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
		else if (msg.msgType.equals("one job fin"))
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
	
	public void regist(long wait)
	{
		String nodeName = "node-" + Integer.toString(id);
		Message msg = new Message(id, ctrlId, "registration", nodeName);
		sendMsg(msg, wait + Library.sendOverhead);
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
		int hashCode = key.hashCode();
		return hashCode % memList.length;
	}
}