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
		String binLongFileName = null, binlogPosition = null, sqlType = null;
		Map<String, String> gtid = null;
		for (Map<String, Object> row : rowResultList) {
			binLongFileName = (String) row.get(ForrestDataConfig.metaBinLogFileName);
			binlogPosition = (String) row.get(ForrestDataConfig.metaBinlogPositionName);
			sqlType = ((String) row.get(ForrestDataConfig.metaSqltypeName));

			if (config.getGtidEnable()) {
				gtid = (Map<String, String>) row.get(ForrestDataConfig.metaGTIDName);
			}

			if (sqlType.equals("DDL")) {
				this.saveBinlogPos(binLongFileName, binlogPosition, gtid);
				continue;
			}

			// 删除meta data info
			if (ForrestDataConfig.ignoreMetaDataName) {
				this.removeMetadataData(row);
			}

			System.out.println(this.getJsonStringFromMap(row));
		}
		

		/*
		 *delete range，update range,insert multi,共用一个binlog posistion, 在for循环中持久化position信息，可能会导致数据丢失。在for循环外持久化position信息，可能会导致数据重复。
		 *
		 */
		this.saveBinlogPos(binLongFileName, binlogPosition, gtid);

		return true;
	}
}
