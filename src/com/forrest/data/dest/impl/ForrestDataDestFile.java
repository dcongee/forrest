package com.forrest.data.dest.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.forrest.data.ForrestData;
import com.forrest.data.config.ForrestDataConfig;
import com.forrest.data.dest.ForrestDataAbstractDestination;
import com.forrest.data.dest.ForrestDataDestination;
import com.forrest.monitor.ForrestMonitor;

public class ForrestDataDestFile extends ForrestDataAbstractDestination implements ForrestDataDestination {
	private static Logger logger = Logger.getLogger(ForrestData.class);
	private OutputStreamWriter op;

	public ForrestDataDestFile(ForrestDataConfig config, ForrestMonitor forrestMonitor) {
		this.config = config;
		this.forrestMonitor = forrestMonitor;
		File file = new File("result.txt");
		try {
			try {
				this.op = new OutputStreamWriter(new FileOutputStream(file), "utf-8");

			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean deliverDest(List<Map<String, Object>> rowResultList) {
		String binLongFileName = null;
		String binlogPosition = null;
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
			// System.out.println(JSON.toJSONString(row));
			try {
				// op.write(JSON.toJSONString(row));
				op.write(this.getJsonStringFromMap(row));
				op.write("\r\n");
				op.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				logger.error("file deliver exception: " + e.getMessage());
				return false;
			}
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
