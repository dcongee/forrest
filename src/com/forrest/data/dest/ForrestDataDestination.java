package com.forrest.data.dest;

import java.util.List;
import java.util.Map;

public interface ForrestDataDestination {
	
	public boolean deliverDest(List<Map<String, Object>> list);

	public void saveBinlogPos(String binLongFileName, String binlogPosition, Map<String, String> gtidMap);
}
