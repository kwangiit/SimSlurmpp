import java.util.LinkedList;

public class Resource 
{
	int numAvailNode;
	LinkedList<String> nodeLL;
	
	public Resource()
	{
		numAvailNode = 0;
		nodeLL = new LinkedList<String>();
	}
	
	public int comResource(Resource dest)
	{
		if (this == null || dest == null)
		{
			return 1;
		}
		else if (numAvailNode != dest.numAvailNode) 
		{
			return 1;
		}
		else if (nodeLL == null || dest.nodeLL == null)
		{
			return 1;
		}
		else
		{
			for (int i = 0; i < numAvailNode; i++)
			{
				if (!(nodeLL.get(i).equals(dest.nodeLL.get(i))))
				{
					return 1;
				}
			}
			return 0;
		}
	}
}
