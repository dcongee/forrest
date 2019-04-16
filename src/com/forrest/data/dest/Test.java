package com.forrest.data.dest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;

public class Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		long length = 10;
		long begin = System.currentTimeMillis();
		for (long i = 0; i < length; i++) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(null, "value");
			map.put("TABLE_NAME", "W1w1w1w1w1w1w1w1w1w1w1w1");
			list.add(map);
		}

		for (Map<String, Object> row : list) {
			System.out.println(JSON.toJSONString(row));
		}

	}

}
