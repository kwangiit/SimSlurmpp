import java.util.*;

public class Job 
{
	String client;
	String jobId;
	String prefix;
	int numNodeRequired;
	int numCoresRequiredPerNode;
	int numNodeTransmitted;
	int numNodeReturnRes;
	String dir;
	String cmd;
	LinkedList<String> nodelist;
	LinkedList<String> ctrls;
	LinkedList<Resource> ctrlNodelist;
	
	String ctrlBackup;
	Resource resBackup;
	
	int numTry;
	long startTime;
	long submitTime;
	long exeTime;
	long finTime;
	long backTime;
}