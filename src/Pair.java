
public class Pair 
{
	Object key;
	Object value;
	Object attemptValue;
	String identifier;
	String type;
	String forWhat;
	
	public Pair(Object key, Object value, Object attemptValue, 
			String identifier, String type, String forWhat)
	{
		this.key = key;
		this.value = value;
		this.attemptValue = attemptValue;
		this.identifier = identifier;
		this.type = type;
		this.forWhat = forWhat;
	}
}
