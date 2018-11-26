package com.forrest.monitor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.forrest.data.RowResult;

public class ForrestMonitor {
	private String readBinlogFile;
	private long readPosition;

	private String writeBinlogFile;
	private String writePosition;

	private Long readBinaryLogSize;
	private Long writeBinaryLogSize;

	private BigDecimal readBinaryLograte;
	private BigDecimal writeBinaryLograte;

	private Date currentTime;

	private RowResult rowResult;

	private Map<String, Object> monitorMap;

	public ForrestMonitor() {
		this.monitorMap = new HashMap<String, Object>();
	}

	public ForrestMonitor(RowResult rowResult) {
		this.rowResult = rowResult;
	}

	public ForrestMonitor(String readBinlogFile, long readPosition, String writeBinlogFile, String writePosition,
			Long readBinaryLogSize, Long writeBinaryLogSize, BigDecimal readBinaryLograte,
			BigDecimal writeBinaryLograte, Date currentTime) {
		this.readBinlogFile = readBinlogFile;
		this.readPosition = readPosition;
		this.writeBinlogFile = writeBinlogFile;
		this.writePosition = writePosition;
		this.readBinaryLogSize = readBinaryLogSize;
		this.writeBinaryLogSize = writeBinaryLogSize;
		this.readBinaryLograte = readBinaryLograte;
		this.writeBinaryLograte = writeBinaryLograte;
		this.currentTime = currentTime;
	}

	public String getReadBinlogFile() {
		return readBinlogFile;
	}

	public void setReadBinlogFile(String readBinlogFile) {
		this.readBinlogFile = readBinlogFile;
	}

	public long getReadPosition() {
		return readPosition;
	}

	public void setReadPosition(long readPosition) {
		this.readPosition = readPosition;
	}

	public String getWriteBinlogFile() {
		return writeBinlogFile;
	}

	public void setWriteBinlogFile(String writeBinlogFile) {
		this.writeBinlogFile = writeBinlogFile;
	}

	public String getWritePosition() {
		return writePosition;
	}

	public void setWritePosition(String writePosition) {
		this.writePosition = writePosition;
	}

	public Long getReadBinaryLogSize() {
		return readBinaryLogSize;
	}

	public void setReadBinaryLogSize(Long readBinaryLogSize) {
		this.readBinaryLogSize = readBinaryLogSize;
	}

	public Long getWriteBinaryLogSize() {
		return writeBinaryLogSize;
	}

	public void setWriteBinaryLogSize(Long writeBinaryLogSize) {
		this.writeBinaryLogSize = writeBinaryLogSize;
	}

	public BigDecimal getReadBinaryLograte() {
		return readBinaryLograte;
	}

	public void setReadBinaryLograte(BigDecimal readBinaryLograte) {
		this.readBinaryLograte = readBinaryLograte;
	}

	public BigDecimal getWriteBinaryLograte() {
		return writeBinaryLograte;
	}

	public void setWriteBinaryLograte(BigDecimal writeBinaryLograte) {
		this.writeBinaryLograte = writeBinaryLograte;
	}

	public Date getCurrentTime() {
		return currentTime;
	}

	public void setCurrentTime(Date currentTime) {
		this.currentTime = currentTime;
	}

	public RowResult getRowResult() {
		return rowResult;
	}

	public void setRowResult(RowResult rowResult) {
		this.rowResult = rowResult;
	}

	public Map<String, Object> getMonitorMap() {
		return monitorMap;
	}

	public void setMonitorMap(Map<String, Object> monitorMap) {
		this.monitorMap = monitorMap;
	}

	public void putReadBinlogInfo(String binglogFileName, Object binlogPosition, Object gtid) {
		this.monitorMap.put("read_master_binlog_file", binglogFileName);
		this.monitorMap.put("read_master_binlog_position", binlogPosition);
		if (gtid != null) {
			this.monitorMap.put("read_master_gtid", gtid);
		}
	}

	public void putExecBinlogInfo(String binglogFileName, Object binlogPosition, Object gtid) {
		this.monitorMap.put("exec_master_binlog_file", binglogFileName);
		this.monitorMap.put("exec_master_binlog_position", binlogPosition);
		if (gtid != null) {
			this.monitorMap.put("read_master_gtid", gtid);
		}
	}

}
