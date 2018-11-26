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
		// try {
		// Thread.sleep(3 * 1000);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
		for (Map<String, Object> row : rowResultList) {
			binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
			binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
			if (((String) row.get(ForrestDataConfig.metaSqltypeName)).equals("DDL")) {
				this.flushMetaData(row);
				if (config.getGtidEnable()) {
					this.saveBinlogPos(binLongFileName, binlogPosition,
							(Map<String, String>) row.get(ForrestDataConfig.metaGTIDName));
				} else {
					this.saveBinlogPos(binLongFileName, binlogPosition, null);
				}
				continue;
			}
			System.out.println(this.getJsonStringFromMap(row));
			if (config.getGtidEnable()) {
				this.saveBinlogPos(binLongFileName, binlogPosition,
						(Map<String, String>) row.get(ForrestDataConfig.metaGTIDName));
			} else {
				this.saveBinlogPos(binLongFileName, binlogPosition, null);
			}

		}
		return true;
	}
}
