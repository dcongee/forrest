package com.forrest.data.dest.impl;

import java.util.List;
import java.util.Map;

import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.monitor.ForrestMonitor;

public class ForrestDataDestStdout extends ForrestDataAbstractDestination implements ForrestDataDestination {

	public ForrestDataDestStdout(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		this.forrestMonitor = forrestMonitor;
		this.config = config;
	}

	public ForrestDataDestStdout() {
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {
		String binLongFileName, binlogPosition;
		for (Map<String, Object> row : rowResultList) {
			binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
			binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
			if (((String) row.get(ForrestDataConfig.metaSqltypeName)).equals("DDL")) {
				this.flushMetaData(row);
				this.saveBinlogPos(binLongFileName, binlogPosition);
				continue;
			}
			System.out.println(this.getJsonStringFromMap(row));
			this.saveBinlogPos((String) row.get(ForrestDataConfig.metaBinLogFileName),
					(String) row.get(ForrestDataConfig.metaBinlogPositionName));

		}
		return true;
	}
}
