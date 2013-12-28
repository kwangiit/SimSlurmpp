import java.util.Comparator;

public class NodelistComp implements Comparator<String>
{
	public int compare(String node1, String node2)
	{
		int node1Id = Integer.parseInt(node1.substring(5));
		int node2Id = Integer.parseInt(node2.substring(5));
		
		if (node1Id < node2Id)
		{
			return -1;
		}
		else
		{
			return 1;
		}
	}
}
