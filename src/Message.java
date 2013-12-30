import java.io.Serializable;

public class Message implements Serializable
{
	int sourceId;
	int destId;
	String msgType;
	Object content;
	
	public Message()
	{
		
	}
	
	public Message(int sourceId, int destId, 
						String msgType, Object content)
	{
		this.sourceId = sourceId;
		this.destId = destId;
		this.msgType = msgType;
		this.content = content;
	}
}
