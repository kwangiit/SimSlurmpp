import java.util.*;
import java.io.*;

import peersim.core.*;
import peersim.config.Configuration;

public class TrafficGene implements Control
{
	private static final String PAR_PROT = "protocol";
	private static final String PAR_WAITTIMEFILE = "waitTimeFile";
	
	private final int pid;
	private final String waitTimeFile;
	
	public TrafficGene(String prefix)
	{
		pid = Configuration.getPid(prefix + "." + PAR_PROT);
		waitTimeFile = Configuration.getString(prefix + "." + PAR_WAITTIMEFILE);
	}
	
	public boolean execute()
	{
		int numNode = Network.size();
		BufferedReader br = null;
		try
		{
			br = new BufferedReader(new FileReader(waitTimeFile));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		TreeSet<Comparator> ts = new TreeSet<Comparator>();
		try
		{
			long wait = 0;
			for (int i = 0; i < numNode; i++)
			{
				wait = Long.parseLong(br.readLine());
				Comparator comp = new Comparator(wait, i);
				ts.add(comp);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		Iterator<Comparator> it = ts.iterator();
		while (it.hasNext())
		{
			Comparator comp = it.next();
			Node node = (Node)Network.get(comp.id);
			PeerProtocol pp = (PeerProtocol)node.getProtocol(pid);
			pp.regist(comp.wait);
		}
		return false;
	}
}