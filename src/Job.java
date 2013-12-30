import java.util.*;
import java.io.Serializable;

public class Job implements Serializable
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
	String argv;
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